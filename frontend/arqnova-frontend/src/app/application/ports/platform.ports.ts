export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}
export interface StateCell<T> {
  (): T;
  set(value: T): void;
  update(update: (value: T) => T): void;
}
export interface StateFactory {
  cell<T>(value: T): StateCell<T>;
}
