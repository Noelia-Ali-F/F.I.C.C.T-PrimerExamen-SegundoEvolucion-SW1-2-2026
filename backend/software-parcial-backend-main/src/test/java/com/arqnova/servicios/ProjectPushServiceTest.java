package com.arqnova.servicios;
import com.arqnova.modelo.Usuario;
import com.arqnova.modelo.ia.ProjectPush.*;
import com.arqnova.servicios.impl.ProjectPushService;
import com.arqnova.servicios.puertos.entrada.UserQueryUseCase;
import com.arqnova.servicios.puertos.salida.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ProjectPushServiceTest {
    final PushDevicePort devices=mock(PushDevicePort.class);final PushDeliveryPort sender=mock(PushDeliveryPort.class);
    final UserQueryUseCase users=mock(UserQueryUseCase.class);final List<Runnable> queue=new ArrayList<>();
    final ProjectPushService service=new ProjectPushService(devices,sender,queue::add,users);
    final Event event=new Event("event-001","p","ArqNova","Nueva actividad");
    @BeforeEach void setup(){when(devices.enabled()).thenReturn(true);when(sender.enabled()).thenReturn(true);when(users.findByUsername("noelia")).thenReturn(Optional.of(Usuario.builder().habilitado(true).build()));}
    @Test void registrationUsesAuthenticatedUserAndDoesNotLetTheClientSelectOtherUser(){
        service.register("noelia","fid-test-001");verify(devices).register("noelia","fid-test-001");
        service.unregister("noelia","fid-test-001");verify(devices).remove("noelia","fid-test-001");
    }
    @Test void deliveryIsAsynchronousAndSameEventDeviceIsNotSentTwice()throws Exception{
        when(devices.recipients("p")).thenReturn(List.of(new Device("fid-test-001","noelia")));
        when(devices.claim("event-001","fid-test-001")).thenReturn(true,false);
        service.notifyChange(event);verify(sender,never()).send(anyString(),any());queue.remove(0).run();
        service.notifyChange(event);queue.remove(0).run();verify(sender,times(1)).send("fid-test-001",event);
    }
    @Test void providerFailureCannotBreakTheSavedApplicationOperation()throws Exception{
        when(devices.recipients("p")).thenReturn(List.of(new Device("fid-test-001","noelia")));when(devices.claim(anyString(),anyString())).thenReturn(true);
        doThrow(new IllegalStateException("Offline")).when(sender).send(anyString(),any());
        service.notifyChange(event);assertThatCode(()->queue.remove(0).run()).doesNotThrowAnyException();verify(devices).releaseClaim("event-001","fid-test-001");
    }
    @Test void disabledPushDoesNotReadDevicesOrScheduleWork(){
        when(sender.enabled()).thenReturn(false);service.notifyChange(event);assertThat(queue).isEmpty();verify(devices,never()).recipients(anyString());
        assertThatThrownBy(()->service.register("noelia","fid-test-001")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsInvalidInstallationsBeforeSaving(){
        assertThatThrownBy(()->service.register("noelia","bad / token")).isInstanceOf(IllegalArgumentException.class);verify(devices,never()).register(anyString(),anyString());
    }
    @Test void failedClaimForOneRecipientDoesNotSkipTheNextDevice()throws Exception{
        when(devices.recipients("p")).thenReturn(List.of(new Device("fid-broken","a"),new Device("fid-working","b")));
        when(devices.claim("event-001","fid-broken")).thenThrow(new IllegalStateException("temporary database failure"));
        when(devices.claim("event-001","fid-working")).thenReturn(true);
        service.notifyChange(event);queue.remove(0).run();
        verify(sender).send("fid-working",event);verify(sender,never()).send("fid-broken",event);
    }
    @Test void failedCleanupOfOneDeliveryDoesNotSkipOtherRecipients()throws Exception{
        when(devices.recipients("p")).thenReturn(List.of(new Device("fid-broken","a"),new Device("fid-working","b")));
        when(devices.claim(anyString(),anyString())).thenReturn(true);
        doThrow(new IllegalStateException("provider offline")).when(sender).send("fid-broken",event);
        doThrow(new IllegalStateException("cleanup offline")).when(devices).releaseClaim("event-001","fid-broken");
        service.notifyChange(event);assertThatCode(()->queue.remove(0).run()).doesNotThrowAnyException();
        verify(sender).send("fid-working",event);
    }
}
