import { describe, expect, it } from 'vitest';
import { matchRoutes } from 'react-router-dom';

import { appRouteObjects, containerDetailUrl } from '/src/app/app-routes';

describe('app routes', () => {
  it('matches and restores a container detail tab', () => {
    const matches = matchRoutes(appRouteObjects, '/containers/demo_container/terminal');

    expect(matches?.[matches.length - 1]?.params).toEqual({ containerName: 'demo_container', tab: 'terminal' });
  });

  it('matches the container management screen', () => {
    const matches = matchRoutes(appRouteObjects, '/containers');
    expect(matches?.[matches.length - 1]?.route.path).toBe('/containers');
  });

  it('matches the image management screen', () => {
    const matches = matchRoutes(appRouteObjects, '/images');
    expect(matches?.[matches.length - 1]?.route.path).toBe('/images');
  });

  it('encodes container names when building detail URLs', () => {
    expect(containerDetailUrl('demo container', 'files')).toBe('/containers/demo%20container/files');
  });
});
