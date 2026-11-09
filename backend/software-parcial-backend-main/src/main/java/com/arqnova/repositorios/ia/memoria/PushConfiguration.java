package com.arqnova.repositorios.ia.memoria;
import com.arqnova.servicios.puertos.salida.*;
import com.arqnova.modelo.ia.ProjectPush.Device;
import org.springframework.context.annotation.*;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.*;

@Configuration
public class PushConfiguration {
    @Bean(destroyMethod="shutdownNow") ExecutorService projectPushExecutor(){
        return Executors.unconfigurableExecutorService(new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100),task->{var t=new Thread(task,"arqnova-push");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()));
    }
    @Bean PushTaskPort pushTasks(@org.springframework.beans.factory.annotation.Qualifier("projectPushExecutor")ExecutorService projectPushExecutor){return task->{
        Runnable dispatch=()->{try{projectPushExecutor.execute(task);}catch(RejectedExecutionException busy){/* Preserve the saved application operation. */}};
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){dispatch.run();}});
        else dispatch.run();
    };}
    @Bean @Profile("dynamodb") PushDevicePort unavailableDynamoPush(){return new PushDevicePort(){
        public boolean enabled(){return false;}
        public void register(String user,String fid){throw new IllegalArgumentException("Push no disponible en este perfil");}
        public void remove(String user,String fid){}
        public List<Device> recipients(String project){return List.of();}
        public boolean claim(String event,String fid){return false;}
        public void releaseClaim(String event,String fid){}
    };}
}
