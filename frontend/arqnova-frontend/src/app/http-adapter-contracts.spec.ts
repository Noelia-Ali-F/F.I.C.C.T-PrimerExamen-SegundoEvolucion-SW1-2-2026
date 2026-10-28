import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting,HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { describe,it,beforeEach,afterEach,expect } from 'vitest';
import { BASE_PATH } from './api/variables';
import { UmlAiCommandService } from './pages/uml-class-diagram/uml-ai-command.service';
import { UmlBackendGeneratorService } from './pages/uml-class-diagram/uml-backend-generator.service';
import { UmlFlutterGeneratorService } from './pages/uml-class-diagram/uml-flutter-generator.service';
import { DEFAULT_UML_MODEL } from './domain/uml/uml-class-diagram.models';
describe('contratos HTTP de los adaptadores hexagonales',()=>{
 let http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:BASE_PATH,useValue:'http://test'}]});http=TestBed.inject(HttpTestingController);});
 afterEach(()=>http.verify());
 it('mantiene el campo multipart image y el resultado de análisis',async()=>{
  const image=new File(['photo'],'diagrama.png',{type:'image/png'});
  const promise=firstValueFrom(TestBed.inject(UmlAiCommandService).analyzeImage(image));
  const request=http.expectOne('http://test/api/v1/uml-ai/image');expect(request.request.body.get('image')).toBe(image);
  const data={status:'READY',provider:'GEMINI',model:'fixture',classes:[],relations:[],warnings:[],message:'ok'};request.flush({datos:data});expect(await promise).toEqual(data);
 });
 it('conserva los bytes y el nombre del archivo del backend generado',async()=>{
  const promise=firstValueFrom(TestBed.inject(UmlBackendGeneratorService).generate({projectName:'Proyecto',basePackage:'com.ejemplo',includeBasicConfig:true,model:DEFAULT_UML_MODEL}));
  const request=http.expectOne('http://test/api/v1/uml-backend/generate');expect(request.request.responseType).toBe('blob');
  const archive=new Blob(['PK-content'],{type:'application/zip'});request.flush(archive,{headers:{'Content-Disposition':'attachment; filename="proyecto.zip"'}});
  const response=await promise;expect(response.body).toBe(archive);expect(response.headers.get('Content-Disposition')).toContain('proyecto.zip');
 });
 it('conserva la descarga binaria de Flutter',async()=>{
  const promise=firstValueFrom(TestBed.inject(UmlFlutterGeneratorService).generate({projectName:'Movil',backendBaseUrl:'http://api',model:DEFAULT_UML_MODEL}));
  const request=http.expectOne('http://test/api/v1/uml-flutter/generate');expect(request.request.responseType).toBe('blob');
  const archive=new Blob(['PK-flutter']);request.flush(archive);expect((await promise).body).toBe(archive);
 });
});
