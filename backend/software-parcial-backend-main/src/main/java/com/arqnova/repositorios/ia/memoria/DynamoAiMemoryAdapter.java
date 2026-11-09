package com.arqnova.repositorios.ia.memoria;

import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.modelo.excepciones.AiMemoryUnavailableException;
import com.arqnova.servicios.puertos.salida.AiMemoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Separate table for AI memory. Does not participate in or replace the application's JPA transactions. */
public final class DynamoAiMemoryAdapter implements AiMemoryPort, AutoCloseable {
    private final DynamoDbClient client;
    private final ObjectMapper json;
    private final String table;
    public DynamoAiMemoryAdapter(DynamoDbClient client,ObjectMapper json,String table){this.client=client;this.json=json;this.table=table;}
    public boolean enabled(){return true;}
    public Submission begin(Turn turn) {
        Optional<Turn> existing=find(turn.projectId(),turn.requestId());
        if(existing.isPresent())return new Submission(existing.get(),false);
        String sort=sort(turn);
        var message=item(turn,sort);var user=item(turn,userSort(turn));
        var reference=new HashMap<>(key(turn.projectId(),"REQUEST#"+turn.requestId()));reference.put("turnKey",s(sort));
        for(int attempt=0;attempt<5;attempt++)try{
            client.transactWriteItems(r->r.transactItems(
                    TransactWriteItem.builder().put(Put.builder().tableName(table).item(jobItem(turn))
                            .conditionExpression("attribute_not_exists(pk)").build()).build(),
                    TransactWriteItem.builder().put(Put.builder().tableName(table).item(message).conditionExpression("attribute_not_exists(pk)").build()).build(),
                    TransactWriteItem.builder().put(Put.builder().tableName(table).item(user).conditionExpression("attribute_not_exists(pk)").build()).build(),
                    TransactWriteItem.builder().put(Put.builder().tableName(table).item(reference).conditionExpression("attribute_not_exists(pk)").build()).build(),
                    TransactWriteItem.builder().update(Update.builder().tableName(table).key(key(turn.projectId(),"CONVERSATION"))
                            .updateExpression("SET createdAt=if_not_exists(createdAt,:now), updatedAt=:now, projectId=:project")
                            .expressionAttributeValues(Map.of(":now",s(turn.createdAt().toString()),":project",s(turn.projectId()))).build()).build()));
            return new Submission(turn,true);
        }catch(TransactionCanceledException duplicate){
            Optional<Turn> winner=find(turn.projectId(),turn.requestId());
            if(winner.isPresent())return new Submission(winner.get(),false);
            if(attempt<4)try{Thread.sleep(5L*(attempt+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AiMemoryUnavailableException();}
        }catch(software.amazon.awssdk.core.exception.SdkException unavailable){throw new AiMemoryUnavailableException();}
        throw new AiMemoryUnavailableException();
    }
    public Optional<Turn> find(String project,String request){
        try{
            var ref=client.getItem(r->r.tableName(table).key(key(project,"REQUEST#"+request)).consistentRead(true)).item();
            if(ref.isEmpty())return Optional.empty();
            var item=client.getItem(r->r.tableName(table).key(key(project,ref.get("turnKey").s())).consistentRead(true)).item();
            return item.isEmpty()?Optional.empty():Optional.of(turn(item));
        }catch(software.amazon.awssdk.core.exception.SdkException unavailable){throw new AiMemoryUnavailableException();}
    }
    public Page history(String project,String cursor,int limit){
        String after=null;
        if(cursor!=null&&!cursor.isBlank())try{
            after=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8);
            if(!after.matches("TURN#[0-9]{19}#[A-Za-z0-9_-]{8,100}"))throw new IllegalArgumentException();
        }catch(IllegalArgumentException error){throw new IllegalArgumentException("Cursor de conversación inválido");}
        try{
            QueryResponse response=query(project,"TURN#",after,limit);
            String next=response.lastEvaluatedKey().isEmpty()?null:Base64.getUrlEncoder().withoutPadding().encodeToString(response.lastEvaluatedKey().get("sk").s().getBytes(StandardCharsets.UTF_8));
            return new Page(response.items().stream().map(this::turn).toList(),next);
        }catch(software.amazon.awssdk.core.exception.SdkException unavailable){throw new AiMemoryUnavailableException();}
    }
    public List<Turn> recentByUser(String project,String user,int limit){
        try{return query(project,"USER#"+encodedUser(user)+"#TURN#",null,limit).items().stream().map(this::turn).toList();}
        catch(software.amazon.awssdk.core.exception.SdkException unavailable){throw new AiMemoryUnavailableException();}
    }
    public boolean finish(Turn turn){
        if(turn.state()==State.PENDING)throw new IllegalArgumentException("Se requiere un resultado final");
        for(int attempt=0;attempt<3;attempt++)try{
            client.transactWriteItems(r->r.transactItems(update(turn,sort(turn)),update(turn,userSort(turn))));
            return true;
        }catch(TransactionCanceledException collision){
            if(collision.cancellationReasons().stream().anyMatch(v->"ConditionalCheckFailed".equals(v.code())))return false;
        }catch(software.amazon.awssdk.core.exception.SdkException unavailable){throw new AiMemoryUnavailableException();}
        throw new AiMemoryUnavailableException();
    }
    @Override public void ensurePending(Turn turn) {
        try {
            client.putItem(r -> r.tableName(table).item(jobItem(turn)).conditionExpression("attribute_not_exists(pk)"));
        } catch (ConditionalCheckFailedException exists) { /* Preserve the lease and cached result. */ }
        catch (software.amazon.awssdk.core.exception.SdkException unavailable) { throw new AiMemoryUnavailableException(); }
    }

    @Override public boolean durableJobs() { return true; }

    private Map<String,AttributeValue> jobKey(Turn turn) {
        return Map.of("pk",s("AI#JOBS"),"sk",s("JOB#" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(turn.projectId().getBytes(StandardCharsets.UTF_8)) + "#" + turn.requestId()));
    }
    private Map<String,AttributeValue> jobItem(Turn turn) {
        var item = new HashMap<>(jobKey(turn));
        item.put("project",s(turn.projectId()));
        item.put("request",s(turn.requestId()));
        return item;
    }

    @Override public JobPage pendingJobs(String cursor, int limit) {
        try {
            var response = client.query(r -> r.tableName(table).consistentRead(true).limit(Math.min(20, limit))
                    .keyConditionExpression("pk=:pk AND begins_with(sk,:prefix)")
                    .expressionAttributeValues(Map.of(":pk",s("AI#JOBS"),":prefix",s("JOB#")))
                    .exclusiveStartKey(cursor == null ? null : Map.of("pk",s("AI#JOBS"),"sk",s(cursor))));
            var turns = new ArrayList<Turn>();
            for (var item : response.items()) {
                var turn = find(item.get("project").s(),item.get("request").s());
                if (turn.isPresent() && turn.get().state() == State.PENDING) turns.add(turn.get());
                else client.deleteItem(r -> r.tableName(table).key(Map.of("pk",item.get("pk"),"sk",item.get("sk"))));
            }
            return new JobPage(turns,response.lastEvaluatedKey().isEmpty() ? null : response.lastEvaluatedKey().get("sk").s());
        } catch (software.amazon.awssdk.core.exception.SdkException unavailable) { throw new AiMemoryUnavailableException(); }
    }

    @Override public Optional<Job> claim(Turn turn, String owner, long now, long until) {
        try {
            client.transactWriteItems(r -> r.transactItems(
                    TransactWriteItem.builder().conditionCheck(ConditionCheck.builder().tableName(table)
                            .key(key(turn.projectId(),sort(turn))).conditionExpression("#state=:pending")
                            .expressionAttributeNames(Map.of("#state","state"))
                            .expressionAttributeValues(Map.of(":pending",s("PENDING"))).build()).build(),
                    TransactWriteItem.builder().update(Update.builder().tableName(table).key(jobKey(turn))
                            .conditionExpression("attribute_not_exists(leaseUntil) OR leaseUntil < :now")
                            .updateExpression("SET #owner=:owner, leaseUntil=:until, attempts=if_not_exists(attempts,:zero)+:one, #project=:project, #request=:request")
                            .expressionAttributeNames(Map.of("#owner","owner","#project","project","#request","request"))
                            .expressionAttributeValues(Map.of(":owner",s(owner),":now",n(now),":until",n(until),
                                    ":zero",n(0),":one",n(1),":project",s(turn.projectId()),":request",s(turn.requestId()))).build()).build()));
            var item = client.getItem(r -> r.tableName(table).key(jobKey(turn)).consistentRead(true)).item();
            if (!owner.equals(item.getOrDefault("owner",s("")).s())) return Optional.empty();
            Turn result = item.containsKey("result") ? json.readValue(item.get("result").s(),Turn.class) : null;
            return Optional.of(new Job(turn,owner,Integer.parseInt(item.get("attempts").n()),result));
        } catch (TransactionCanceledException busy) { return Optional.empty(); }
        catch (software.amazon.awssdk.core.exception.SdkException unavailable) { throw new AiMemoryUnavailableException(); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException("Resultado durable inválido",invalid); }
    }

    @Override public boolean renew(Job job, long now, long until) {
        return updateJob(job,now,"SET leaseUntil=:until",Map.of(":until",n(until)));
    }
    @Override public boolean stage(Job job, Turn result, long now) {
        requireSameRequest(job, result);
        return updateJob(job,now,"SET #result=:result",Map.of(":result",s(serialize(result))));
    }
    private boolean updateJob(Job job, long now, String expression, Map<String,AttributeValue> values) {
        try {
            var all = new HashMap<>(values);
            all.put(":owner",s(job.owner()));all.put(":now",n(now));
            var names = new HashMap<String,String>();names.put("#owner","owner");
            if (expression.contains("#result")) names.put("#result","result");
            client.updateItem(r -> r.tableName(table).key(jobKey(job.turn())).updateExpression(expression)
                    .conditionExpression("#owner=:owner AND leaseUntil >= :now")
                    .expressionAttributeNames(names).expressionAttributeValues(all));
            return true;
        } catch (ConditionalCheckFailedException lost) { return false; }
        catch (software.amazon.awssdk.core.exception.SdkException unavailable) { throw new AiMemoryUnavailableException(); }
    }

    @Override public boolean complete(Job job, Turn result, long now) {
        requireSameRequest(job, result);
        try {
            client.transactWriteItems(r -> r.transactItems(update(result,sort(result)),update(result,userSort(result)),
                    TransactWriteItem.builder().delete(Delete.builder().tableName(table).key(jobKey(job.turn()))
                            .conditionExpression("#owner=:owner AND leaseUntil >= :now")
                            .expressionAttributeNames(Map.of("#owner","owner"))
                            .expressionAttributeValues(Map.of(":owner",s(job.owner()),":now",n(now))).build()).build()));
            return true;
        } catch (TransactionCanceledException lost) { return false; }
        catch (software.amazon.awssdk.core.exception.SdkException unavailable) { throw new AiMemoryUnavailableException(); }
    }

    @Override public void release(Job job) {
        try {
            client.updateItem(r -> r.tableName(table).key(jobKey(job.turn())).updateExpression("SET leaseUntil=:zero")
                    .conditionExpression("#owner=:owner").expressionAttributeNames(Map.of("#owner","owner"))
                    .expressionAttributeValues(Map.of(":owner",s(job.owner()),":zero",n(0))));
        } catch (ConditionalCheckFailedException lost) { /* A new worker owns this job. */ }
        catch (software.amazon.awssdk.core.exception.SdkException unavailable) { throw new AiMemoryUnavailableException(); }
    }
    private void requireSameRequest(Job job, Turn result) {
        var original=job.turn();
        if (result.state()==State.PENDING || !original.projectId().equals(result.projectId())
                || !original.requestId().equals(result.requestId()) || !original.authorId().equals(result.authorId())
                || original.mode()!=result.mode() || !original.instruction().equals(result.instruction())
                || !original.createdAt().equals(result.createdAt())) {
            throw new IllegalArgumentException("El resultado no corresponde al trabajo reservado");
        }
    }

    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }

    private TransactWriteItem update(Turn turn,String sort){
        return TransactWriteItem.builder().update(Update.builder().tableName(table).key(key(turn.projectId(),sort))
                .updateExpression("SET payload=:payload, #state=:finished")
                .conditionExpression("#state=:pending").expressionAttributeNames(Map.of("#state","state"))
                .expressionAttributeValues(Map.of(":payload",s(serialize(turn)),":finished",s(turn.state().name()),":pending",s(State.PENDING.name()))).build()).build();
    }
    private QueryResponse query(String project,String prefix,String after,int limit){
        return client.query(QueryRequest.builder().tableName(table).consistentRead(true).scanIndexForward(false).limit(limit)
                .keyConditionExpression("pk=:project AND begins_with(sk,:prefix)")
                .expressionAttributeValues(Map.of(":project",s("PROJECT#"+project),":prefix",s(prefix)))
                .exclusiveStartKey(after==null?null:key(project,after)).build());
    }
    private Map<String,AttributeValue> item(Turn turn,String sort){
        var item=new HashMap<>(key(turn.projectId(),sort));item.put("payload",s(serialize(turn)));item.put("state",s(turn.state().name()));return item;
    }
    private Turn turn(Map<String,AttributeValue> item){
        try{return json.readValue(item.get("payload").s(),Turn.class);}
        catch(Exception error){throw new IllegalStateException("Memoria IA incompatible",error);}
    }
    private String serialize(Turn turn){
        try{String value=json.writeValueAsString(turn);if(value.getBytes(StandardCharsets.UTF_8).length>250_000)throw new IllegalArgumentException("Interacción demasiado grande");return value;}
        catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalArgumentException("Interacción inválida",error);}
    }
    private static String sort(Turn turn){return "TURN#"+String.format(Locale.ROOT,"%019d",turn.createdAt().toEpochMilli())+"#"+turn.requestId();}
    private static String userSort(Turn turn){return "USER#"+encodedUser(turn.authorId())+"#"+sort(turn);}
    private static String encodedUser(String user){return Base64.getUrlEncoder().withoutPadding().encodeToString(user.getBytes(StandardCharsets.UTF_8));}
    private static Map<String,AttributeValue> key(String project,String sort){return Map.of("pk",s("PROJECT#"+project),"sk",s(sort));}
    private static AttributeValue s(String value){return AttributeValue.builder().s(value).build();}
    public void close(){client.close();}
}
