import React from 'react';
import ReactDOM from 'react-dom/client';

import { App } from '/src/app/app';
import { Providers } from '/src/app/providers';
import '/src/styles/globals.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <Providers>
      <App />
    </Providers>
  </React.StrictMode>
);
