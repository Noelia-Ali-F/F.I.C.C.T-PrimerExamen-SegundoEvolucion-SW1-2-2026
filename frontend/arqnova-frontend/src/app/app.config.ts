import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
} from "@angular/core";
import { provideRouter } from "@angular/router";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { provideAnimationsAsync } from "@angular/platform-browser/animations/async";

import { routes } from "./app.routes";
import { BASE_PATH } from "./api/variables";
import { authInterceptor } from "./auth/auth.interceptor";


export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    {
      provide: BASE_PATH,
      useValue:
        (window as any).__env?.apiUrl !== undefined
          ? (window as any).__env.apiUrl
          : ["localhost", "127.0.0.1"].includes(window.location.hostname)
            ? "http://localhost:8080"
            : "https://workflow-service-930110874838.us-central1.run.app",
    },
  ],
};
