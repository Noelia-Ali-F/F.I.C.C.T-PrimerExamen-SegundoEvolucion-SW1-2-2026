import { HttpClient } from '@angular/common/http';
import { Injectable,Inject } from '@angular/core';
import { BASE_PATH } from '../api/variables';
import { UserAdminUseCases } from '../application/workspace.use-cases';
import { HttpAdminUsersAdapter } from '../infrastructure/adapters/http-admin-users.adapter';
export type * from '../domain/auth/admin.models';
@Injectable({providedIn:'root'})export class AdminUsersService extends UserAdminUseCases {constructor(http:HttpClient,@Inject(BASE_PATH)basePath:string){super(new HttpAdminUsersAdapter(http,basePath));}}
