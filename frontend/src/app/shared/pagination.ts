import { map, type Observable } from 'rxjs';

export interface UiPage<T> {
  items: T[];
  total: number;
}

export function toPage<T>(res: any): UiPage<T> {
  return {
    items: res?.content ?? res?.items ?? [],
    total: res?.page?.totalElements ?? res?.totalElements ?? res?.total ?? 0,
  };
}

export function mapPage<T>(source: Observable<any>): Observable<UiPage<T>> {
  return source.pipe(map((response) => toPage<T>(response)));
}
