package com.arqnova.servicios.puertos.salida;
public interface AiTaskPort {
    void schedule(Runnable task);
    default AutoCloseable heartbeat(Runnable renewal) { return () -> {}; }
}
