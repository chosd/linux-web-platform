import { ReactNode } from 'react';
import { BrowserRouter } from 'react-router-dom';

import { ThemeProvider } from '/src/shared/contexts/theme-context';

export function Providers({ children }: { children: ReactNode }) {
  return (
    <BrowserRouter>
      <ThemeProvider>{children}</ThemeProvider>
    </BrowserRouter>
  );
}
