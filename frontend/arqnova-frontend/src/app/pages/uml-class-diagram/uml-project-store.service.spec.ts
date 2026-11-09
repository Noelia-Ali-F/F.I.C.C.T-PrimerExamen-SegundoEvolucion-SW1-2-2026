import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BASE_PATH } from '../../api/variables';
import { DEFAULT_UML_MODEL, UmlProject } from './uml-class-diagram.models';
import { UmlProjectStoreService } from './uml-project-store.service';

describe('UmlProjectStoreService PostgreSQL', () => {
  let store:UmlProjectStoreService;
  let http:HttpTestingController;
  const api='http://test/api/v1/projects';
  const dto=(id:string,name:string)=>({id,name,description:'',status:'EN_EDICION',owner:'admin',memberRole:'OWNER',createdAt:'2026-01-01T00:00:00',updatedAt:'2026-01-01T00:00:00'});

  beforeEach(()=>{
    localStorage.clear();
    TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:BASE_PATH,useValue:'http://test'}]});
    store=TestBed.inject(UmlProjectStoreService);http=TestBed.inject(HttpTestingController);
  });
  afterEach(()=>http.verify());

  it('lista proyectos desde backend y actualiza la cache',async()=>{
    const promise=firstValueFrom(store.list());
    http.expectOne(api).flush({datos:[dto('a','Veterinaria'),dto('b','Biblioteca')]});
    const projects=await promise;
    expect(projects.map(value=>value.id)).toEqual(['a','b']);
    expect(store.cachedList()).toHaveLength(2);
  });

  it('crea proyecto con un único POST, lo marca remoto y no guarda un diagrama vacío',async()=>{
    const promise=firstValueFrom(store.create('Veterinaria','','EN_EDICION','vet'));
    const create=http.expectOne(api);expect(create.request.method).toBe('POST');expect(create.request.body.id).toBe('vet');
    create.flush({datos:dto('vet','Veterinaria')});
    expect((await promise).modelVersion).toBe(0);
    expect(JSON.parse(localStorage.getItem('uml-projects-postgres-migration-v1')!)[`vet`]).toBeTruthy();
    http.expectNone(`${api}/vet/diagram`);
  });

  it('abre proyecto y diagrama aislados por projectId',async()=>{
    const promise=firstValueFrom(store.get('vet'));
    http.expectOne(`${api}/vet`).flush({datos:dto('vet','Veterinaria')});
    http.expectOne(`${api}/vet/diagram`).flush({datos:{projectId:'vet',modelVersion:4,model:{...DEFAULT_UML_MODEL,title:'Veterinaria',classes:[]}}});
    const project=await promise;expect(project.id).toBe('vet');expect(project.model.classes).toHaveLength(0);expect(project.modelVersion).toBe(4);
  });

  it('migra legacy una vez preservando projectId y modelo',async()=>{
    const legacy:UmlProject={id:'legacy-veterinaria',name:'Veterinaria',description:'',status:'EN_EDICION',createdAt:'2026-01-01',updatedAt:'2026-01-01',model:{...DEFAULT_UML_MODEL,title:'Veterinaria',classes:[{...DEFAULT_UML_MODEL.classes[0],id:'mascota',name:'Mascota'}]},modelVersion:0};
    localStorage.setItem('uml-projects-v1',JSON.stringify([legacy]));
    const promise=firstValueFrom(store.list());
    http.expectOne(api).flush({datos:[]});
    const create=http.expectOne(api);expect(create.request.body.id).toBe('legacy-veterinaria');create.flush({datos:dto(legacy.id,legacy.name)});
    const diagram=http.expectOne(`${api}/legacy-veterinaria/diagram`);expect(diagram.request.body.classes[0].name).toBe('Mascota');diagram.flush({datos:{projectId:legacy.id,modelVersion:1,model:legacy.model}});
    http.expectOne(api).flush({datos:[dto(legacy.id,legacy.name)]});
    await promise;
    expect(JSON.parse(localStorage.getItem('uml-projects-postgres-migration-v1')!)[legacy.id]).toBeTruthy();
  });

  it('una migración fallida conserva localStorage y no crea la marca',async()=>{
    const legacy:UmlProject={id:'legacy-veterinaria',name:'Veterinaria',description:'',status:'EN_EDICION',createdAt:'2026-01-01',updatedAt:'2026-01-01',model:DEFAULT_UML_MODEL,modelVersion:0};
    localStorage.setItem('uml-projects-v1',JSON.stringify([legacy]));
    const promise=firstValueFrom(store.list());http.expectOne(api).flush({datos:[]});http.expectOne(api).flush({mensaje:'error'},{status:500,statusText:'Error'});http.expectOne(api).flush({datos:[]});await promise;
    expect(store.cached('legacy-veterinaria')).not.toBeNull();expect(localStorage.getItem('uml-projects-postgres-migration-v1')).toBeNull();
  });

  it('no repite una migración ya marcada',async()=>{
    const legacy:UmlProject={id:'legacy-veterinaria',name:'Veterinaria',description:'',status:'EN_EDICION',createdAt:'2026-01-01',updatedAt:'2026-01-01',model:DEFAULT_UML_MODEL,modelVersion:1};
    localStorage.setItem('uml-projects-v1',JSON.stringify([legacy]));localStorage.setItem('uml-projects-postgres-migration-v1',JSON.stringify({[legacy.id]:'2026-01-01'}));
    const promise=firstValueFrom(store.list());http.expectOne(api).flush({datos:[dto(legacy.id,legacy.name)]});
    expect((await promise).filter(project=>project.id===legacy.id)).toHaveLength(1);
  });

  it('elimina de la cache un proyecto remoto borrado por otro usuario y no lo recrea',async()=>{
    const stale:UmlProject={...dto('hospital','hospital'),status:'EN_EDICION',owner:'Alison',memberRole:'EDITOR',model:DEFAULT_UML_MODEL,modelVersion:2};
    localStorage.setItem('uml-projects-v1',JSON.stringify([stale]));

    const promise=firstValueFrom(store.list());
    const list=http.expectOne(api);expect(list.request.method).toBe('GET');list.flush({datos:[]});

    expect(await promise).toEqual([]);
    expect(store.cached('hospital')).toBeNull();
    http.expectNone(api, 'un proyecto remoto eliminado no debe volver a enviarse mediante POST');
  });

  it('guarda con un PUT y actualiza la versión desde datos',async()=>{
    localStorage.setItem('uml-projects-v1',JSON.stringify([{...dto('vet','Veterinaria'),model:DEFAULT_UML_MODEL,modelVersion:2}]));
    const promise=firstValueFrom(store.saveModel('vet',DEFAULT_UML_MODEL));
    const request=http.expectOne(`${api}/vet/diagram`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('X-Model-Version')).toBe('2');
    request.flush({datos:{projectId:'vet',modelVersion:3,model:DEFAULT_UML_MODEL}});
    expect((await promise).modelVersion).toBe(3);
    expect(store.cached('vet')?.modelVersion).toBe(3);
  });

  it('HTTP 500 conserva el modelo local sin incrementar versión',async()=>{
    localStorage.setItem('uml-projects-v1',JSON.stringify([{...dto('vet','Veterinaria'),model:DEFAULT_UML_MODEL,modelVersion:2}]));
    const changed={...DEFAULT_UML_MODEL,title:'Pendiente'};
    const promise=firstValueFrom(store.saveModel('vet',changed)).catch(error=>error);
    http.expectOne(`${api}/vet/diagram`).flush({mensaje:'error'},{status:500,statusText:'Error'});
    expect((await promise).status).toBe(500);
    expect(store.cached('vet')?.modelVersion).toBe(2);
    expect(store.cached('vet')?.model.title).toBe('Pendiente');
  });

  it('conflicto de versión conserva el último modelo en cache',async()=>{
    const project:UmlProject={id:'vet',name:'Veterinaria',description:'',status:'EN_EDICION',createdAt:'2026-01-01',updatedAt:'2026-01-01',model:DEFAULT_UML_MODEL,modelVersion:2};
    localStorage.setItem('uml-projects-v1',JSON.stringify([project]));
    const changed={...DEFAULT_UML_MODEL,title:'Veterinaria actualizada',classes:[]};
    const promise=firstValueFrom(store.saveModel('vet',changed)).catch(error=>error);
    const request=http.expectOne(`${api}/vet/diagram`);expect(request.request.headers.get('X-Model-Version')).toBe('2');request.flush({mensaje:'conflicto'},{status:409,statusText:'Conflict'});await promise;
    expect(store.cached('vet')?.model.title).toBe('Veterinaria actualizada');
  });
});
