import { signal } from '@angular/core';
import { KeyValueStore, StateCell, StateFactory } from '../../application/ports/platform.ports';
export class BrowserKeyValueStore implements KeyValueStore {
 constructor(private readonly storage:Storage=localStorage){}
 getItem(key:string){return this.storage.getItem(key);}
 setItem(key:string,value:string){this.storage.setItem(key,value);}
 removeItem(key:string){this.storage.removeItem(key);}
}
export class AngularStateFactory implements StateFactory { cell<T>(value:T):StateCell<T>{return signal(value);} }
