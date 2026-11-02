package com.arqnova.repositorios.ia.memoria;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import java.util.concurrent.ExecutorService;
import static org.mockito.Mockito.*;

class PushConfigurationTest {
    @Test void doesNotDispatchBeforeDatabaseCommit(){
        var executor=mock(ExecutorService.class);var task=mock(Runnable.class);var port=new PushConfiguration().pushTasks(executor);
        TransactionSynchronizationManager.initSynchronization();
        try{port.schedule(task);verifyNoInteractions(executor);TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);verify(executor).execute(task);}
        finally{TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void rollbackDoesNotProduceNotifications(){
        var executor=mock(ExecutorService.class);var port=new PushConfiguration().pushTasks(executor);
        TransactionSynchronizationManager.initSynchronization();
        try{port.schedule(()->{});TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));verifyNoInteractions(executor);}
        finally{TransactionSynchronizationManager.clearSynchronization();}
    }
}
