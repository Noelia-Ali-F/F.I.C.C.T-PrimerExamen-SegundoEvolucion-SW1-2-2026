package com.arqnova.controladores.configuracion;
import com.arqnova.servicios.*;
import com.arqnova.servicios.impl.*;
import com.arqnova.servicios.persistencia.UmlProjectPersistenceService;
import com.arqnova.servicios.puertos.salida.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import java.util.Properties;
@Configuration
public class HexagonalConfiguration {
 @Bean com.arqnova.servicios.puertos.entrada.ProjectPushUseCase projectPush(PushDevicePort devices,PushDeliveryPort delivery,PushTaskPort tasks,com.arqnova.servicios.puertos.entrada.UserQueryUseCase users){return new ProjectPushService(devices,delivery,tasks,users);}
 @Bean com.arqnova.servicios.puertos.entrada.ProjectActivityUseCase projectActivity(com.arqnova.servicios.puertos.entrada.ProjectUseCase projects,UmlCollaborationActivityDataPort activities){return new ProjectActivityService(projects,activities,java.time.ZoneId.systemDefault());}
 @Bean com.arqnova.servicios.puertos.entrada.AiConversationUseCase aiConversation(AiMemoryPort memory,AiConversationProviderPort provider,AiConversationEventsPort events,AiTaskPort tasks,com.arqnova.servicios.puertos.entrada.ProjectUseCase projects,com.arqnova.servicios.puertos.entrada.UserQueryUseCase users,AiModelMutationPort mutations,UmlCollaborationActivityDataPort activities){return new AiConversationService(memory,provider,events,tasks,projects,users,java.time.Clock.systemUTC(),mutations,activities);}
 @Bean com.arqnova.servicios.puertos.entrada.TokenValidationUseCase tokenValidation(AuthTokenPort tokens){return new TokenValidationService(tokens);}
 @Bean com.arqnova.servicios.puertos.entrada.AvatarQueryUseCase avatarQuery(AvatarStoragePort avatars){return new AvatarQueryService(avatars);}
 @Bean AuthService authService(UserStore users,AuthTokenPort tokens,PasswordPort passwords){return new AuthServiceImpl(users,tokens,passwords);}
 @Bean UsuarioAdminService usuarioAdminService(UserStore users,DepartamentoStore departments,PasswordPort passwords,AvatarStoragePort avatars){return new UsuarioAdminServiceImpl(users,departments,passwords,avatars);}
 @Bean DepartamentoService departamentoService(DepartamentoStore departments){return new DepartamentoService(departments);}
 @Bean UsuarioConsultaService usuarioConsultaService(UserStore users){return new UsuarioConsultaService(users);}
 @Bean CodigoSeguimientoGenerator codigoSeguimientoGenerator(){return new CodigoSeguimientoGenerator();}
 @Bean UmlElementLockService umlElementLockService(Environment env){return new UmlElementLockService(env.getProperty("uml.collaboration.lock-ttl-ms",Long.class,45000L));}
 @Bean @org.springframework.context.annotation.Primary UmlAiCommandService umlAiCommandService(UmlInterpretationPort ai){return new UmlAiCommandServiceImpl(ai);}
 @Bean com.arqnova.servicios.puertos.entrada.UmlImageAnalysisUseCase umlImageAnalysis(UmlInterpretationPort ai){return new UmlImageAnalysisService(ai);}
 @Bean UmlBackendGeneratorService umlBackendGeneratorService(BackendGenerationPort output){return new UmlBackendGeneratorServiceImpl(output);}
 @Bean UmlFlutterGeneratorService umlFlutterGeneratorService(FlutterGenerationPort output){return new UmlFlutterGeneratorServiceImpl(output);}
 @Bean UmlCollaborationService umlCollaborationService(GraphDataPort graph,CollaborationSessionStore sessions,UmlProjectPersistenceService projects,UmlDiagramDataPort diagrams,UmlCollaborationActivityDataPort activities,PlatformTransactionManager transactions,Environment environment,com.arqnova.servicios.puertos.entrada.ProjectPushUseCase push,UmlElementLockService locks){
  Properties policies=new Properties();
  policies.setProperty("apply","PROPAGATION_REQUIRED");
  policies.setProperty("recordActivity","PROPAGATION_REQUIRED");
  policies.setProperty("activities","PROPAGATION_REQUIRED,readOnly");
  UmlCollaborationService service=transactional(new UmlCollaborationService(graph,sessions,projects,diagrams,activities,push,locks),transactions,policies);
  if(!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dynamodb")))return service;
  // Keep the existing in-process collaboration serialization through the commit itself.
  // Otherwise the next WebSocket event can read an old revision after apply returns but before commit.
  var serialized=new org.springframework.aop.framework.ProxyFactory(service);
  serialized.setProxyTargetClass(true);
  Object guard=new Object();
  serialized.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation->{
   if("apply".equals(invocation.getMethod().getName()))synchronized(guard){return invocation.proceed();}
   return invocation.proceed();
  });
  return (UmlCollaborationService)serialized.getProxy();
 }
 @Bean UmlProjectPersistenceService umlProjectPersistenceService(ProjectDataPort projects,ProjectMemberDataPort members,UmlDiagramDataPort diagrams,UserDataPort users,UmlCollaborationActivityDataPort activities,PlatformTransactionManager transactions,UmlElementLockService locks,CollaborationSessionStore sessions){
  Properties policies=new Properties();
  policies.setProperty("createProject","PROPAGATION_REQUIRED");
  policies.setProperty("findProjectById","PROPAGATION_REQUIRED,readOnly");
  policies.setProperty("saveDiagram","PROPAGATION_REQUIRED");
  policies.setProperty("saveDiagramFromRest","PROPAGATION_REQUIRED");
  policies.setProperty("recordAiChanges","PROPAGATION_REQUIRED");
  policies.setProperty("findDiagramByProjectId","PROPAGATION_REQUIRED,readOnly");
  policies.setProperty("addMember","PROPAGATION_REQUIRED");
  policies.setProperty("acceptInvitation","PROPAGATION_REQUIRED");
  policies.setProperty("deleteProject","PROPAGATION_REQUIRED");
  policies.setProperty("listAccessible","PROPAGATION_REQUIRED,readOnly");
  policies.setProperty("requireAccess","PROPAGATION_REQUIRED,readOnly");
  policies.setProperty("updateProject","PROPAGATION_REQUIRED");
  policies.setProperty("deleteProjectAuthorized","PROPAGATION_REQUIRED");
  var service=transactional(new UmlProjectPersistenceService(projects,members,diagrams,users,activities,locks,sessions),transactions,policies);
  var serialized=new org.springframework.aop.framework.ProxyFactory(service);serialized.setProxyTargetClass(true);
  serialized.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation->{
   if("saveDiagramFromRest".equals(invocation.getMethod().getName()))synchronized(locks){return invocation.proceed();}
   return invocation.proceed();
  });
  return (UmlProjectPersistenceService)serialized.getProxy();
 }
 @SuppressWarnings("unchecked") private <T>T transactional(T target,PlatformTransactionManager manager,Properties policies){
  TransactionProxyFactoryBean factory=new TransactionProxyFactoryBean();
  factory.setTransactionManager(manager);factory.setTarget(target);factory.setProxyTargetClass(true);factory.setTransactionAttributes(policies);factory.afterPropertiesSet();
  return (T)factory.getObject();
 }
}
