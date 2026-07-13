import type { RouteObject } from 'react-router-dom';

export const dashboardPath = '/dashboard';
export const containersPath = '/containers';
export const imagesPath = '/images';
export const containerDetailPath = '/containers/:containerName/:tab';

export const appRouteObjects: RouteObject[] = [
  { path: dashboardPath },
  { path: containersPath },
  { path: imagesPath },
  { path: containerDetailPath }
];

export function containerDetailUrl(containerName: string, tab: string) {
  return `/containers/${encodeURIComponent(containerName)}/${tab}`;
}
