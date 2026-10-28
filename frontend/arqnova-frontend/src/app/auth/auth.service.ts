import { Injectable,inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AutenticacinService } from '../api/api/autenticacin.service';
import { BASE_PATH } from '../api/variables';
import { AuthUseCases } from '../application/auth.use-cases';
import { HttpIdentityAdapter } from '../infrastructure/adapters/http-identity.adapter';
import { BrowserKeyValueStore,AngularStateFactory } from '../infrastructure/adapters/browser-platform.adapters';
export type { UserContext } from '../domain/auth/user-context';
@Injectable({providedIn:'root'})
export class AuthService extends AuthUseCases {
 constructor(){super(new HttpIdentityAdapter(inject(AutenticacinService),inject(HttpClient),inject(BASE_PATH)),new BrowserKeyValueStore(),new AngularStateFactory());}
}
