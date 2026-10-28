import { HttpClient } from '@angular/common/http';
import { Injectable,inject } from '@angular/core';
import { BASE_PATH } from '../../api/variables';
import { UmlProjectUseCases } from '../../application/uml-project.use-cases';
import { HttpProjectRepositoryAdapter } from '../../infrastructure/adapters/http-project-repository.adapter';
import { BrowserKeyValueStore } from '../../infrastructure/adapters/browser-platform.adapters';
@Injectable({providedIn:'root'})
export class UmlProjectStoreService extends UmlProjectUseCases {
 constructor(){super(new HttpProjectRepositoryAdapter(inject(HttpClient),inject(BASE_PATH)),new BrowserKeyValueStore());}
}
