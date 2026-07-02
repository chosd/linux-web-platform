import { ReactNode } from 'react';

import { ThemeProvider } from '/src/shared/contexts/theme-context';

export function Providers({ children }: { children: ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}
