import { useCallback, useEffect, useRef, useState } from 'react';
import { FitAddon } from '@xterm/addon-fit';
import { Terminal } from '@xterm/xterm';
import '@xterm/xterm/css/xterm.css';

import {
  ConnectionStatus,
  defaultTerminalWsUrl,
  terminalUserId,
  welcomeMessage
} from '/src/features/terminal/config/terminal-config';
import { messageForClose } from '/src/features/terminal/lib/terminal-close-message';
import { Button } from '/src/shared/components/button';
import { StatusBadge } from '/src/shared/components/status-badge';

import styles from './terminal-view.module.css';

type TerminalViewProps = {
  backLabel?: string;
  containerName: string;
  displayName: string;
  onBack: () => void;
};

export function TerminalView({ backLabel = 'Dashboard', containerName, displayName, onBack }: TerminalViewProps) {
  const terminalElementRef = useRef<HTMLDivElement | null>(null);
  const terminalRef = useRef<Terminal | null>(null);
  const fitAddonRef = useRef<FitAddon | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const connectionIdRef = useRef(0);
  const reconnectTimerRef = useRef<number | null>(null);
  const resizeObserverRef = useRef<ResizeObserver | null>(null);
  const [status, setStatus] = useState<ConnectionStatus>('Disconnected');
  const [statusMessage, setStatusMessage] = useState('Not connected.');

  const fitTerminal = useCallback(() => {
    requestAnimationFrame(() => {
      fitAddonRef.current?.fit();
    });
  }, []);

  const disconnect = useCallback(() => {
    connectionIdRef.current += 1;
    if (reconnectTimerRef.current !== null) {
      window.clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    const socket = socketRef.current;
    socketRef.current = null;
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      socket.close(1000, 'Client disconnected.');
    }
  }, []);

  const connect = useCallback(() => {
    disconnect();
    const terminal = terminalRef.current;
    if (!terminal) {
      return;
    }

    const baseWsUrl = import.meta.env.VITE_TERMINAL_WS_URL || defaultTerminalWsUrl;
    const wsUrl = `${baseWsUrl}?userId=${encodeURIComponent(terminalUserId)}&containerName=${encodeURIComponent(
      containerName
    )}`;
    const connectionId = connectionIdRef.current;
    setStatus('Connecting');
    setStatusMessage(`Connecting to ${displayName}`);
    terminal.writeln(`\r\n[system] Connecting to ${displayName}...`);

    const socket = new WebSocket(wsUrl);
    socketRef.current = socket;

    socket.onopen = () => {
      if (connectionIdRef.current !== connectionId || socketRef.current !== socket) {
        socket.close(1000, 'Superseded connection.');
        return;
      }
      setStatus('Connected');
      setStatusMessage('Connected. Commands are sent to the Docker terminal.');
      terminal.writeln('\r\n[system] Connected. Type a command and press Enter.\r\n');
      terminal.focus();
      fitTerminal();
    };

    socket.onmessage = (event) => {
      if (typeof event.data === 'string') {
        terminal.write(event.data);
      }
    };

    socket.onerror = () => {
      if (connectionIdRef.current !== connectionId || socketRef.current !== socket) {
        return;
      }
      setStatus('Error');
      setStatusMessage('Connection error. Check that the backend is running and reachable.');
      terminal.writeln('\r\n[system] Connection error. Check backend and Docker status.');
    };

    socket.onclose = (event) => {
      if (socketRef.current === socket) {
        socketRef.current = null;
        const userMessage = messageForClose(event);
        setStatus((current) => (current === 'Error' || event.code >= 1011 ? 'Error' : 'Disconnected'));
        setStatusMessage(userMessage);
        terminal.writeln(`\r\n[system] ${userMessage}`);
      }
    };
  }, [containerName, disconnect, displayName, fitTerminal]);

  useEffect(() => {
    if (!terminalElementRef.current) {
      return;
    }

    const terminal = new Terminal({
      allowProposedApi: false,
      cursorBlink: true,
      cursorStyle: 'bar',
      convertEol: true,
      fontFamily: '"Cascadia Mono", "Fira Code", Consolas, monospace',
      fontSize: 14,
      scrollback: 3000,
      theme: {
        background: '#101418',
        foreground: '#e7edf3',
        cursor: '#f4d35e',
        selectionBackground: '#3b5268'
      }
    });
    const fitAddon = new FitAddon();
    terminal.loadAddon(fitAddon);
    terminal.open(terminalElementRef.current);
    welcomeMessage.forEach((line) => terminal.writeln(line));

    terminal.onData((data) => {
      const socket = socketRef.current;
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(data);
      }
    });

    terminalRef.current = terminal;
    fitAddonRef.current = fitAddon;
    fitTerminal();
    connect();

    const handleResize = () => fitTerminal();
    const handlePageHide = () => disconnect();
    window.addEventListener('resize', handleResize);
    window.addEventListener('pagehide', handlePageHide);
    window.addEventListener('beforeunload', handlePageHide);

    if ('ResizeObserver' in window && terminalElementRef.current) {
      resizeObserverRef.current = new ResizeObserver(() => fitTerminal());
      resizeObserverRef.current.observe(terminalElementRef.current);
    }

    return () => {
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('pagehide', handlePageHide);
      window.removeEventListener('beforeunload', handlePageHide);
      resizeObserverRef.current?.disconnect();
      resizeObserverRef.current = null;
      disconnect();
      terminal.dispose();
      terminalRef.current = null;
      fitAddonRef.current = null;
    };
  }, [connect, disconnect, fitTerminal]);

  return (
    <main className={styles.page}>
      <header className={styles.toolbar}>
        <div className={styles.titleGroup}>
          <h1>Linux Terminal Playground</h1>
          <div className={styles.subtitle}>{displayName}</div>
          <div className={styles.connectionRow}>
            <StatusBadge label={status} />
            <span className={styles.statusMessage}>{statusMessage}</span>
          </div>
        </div>
        <div className={styles.actions}>
          <Button type="button" onClick={onBack}>
            {backLabel}
          </Button>
          <Button type="button" onClick={connect} disabled={status === 'Connecting'}>
            Reconnect
          </Button>
        </div>
      </header>
      <div className={styles.workspace}>
        <section className={styles.shell} aria-label="Linux terminal">
          <div className={styles.host}>
            <div ref={terminalElementRef} className={styles.terminalSurface} />
          </div>
        </section>
        <aside className={styles.usagePanel} aria-label="Basic terminal usage">
          <h2>Usage</h2>
          <ul>
            <li>Type commands directly in the terminal.</li>
            <li>Press Enter to run the current command.</li>
            <li>Account: student</li>
            <li>All commands run inside the selected Docker container.</li>
            <li>Reconnect attaches to the same container.</li>
          </ul>
          <h2>Try</h2>
          <code>pwd</code>
          <code>whoami</code>
          <code>ls -al</code>
          <code>echo hello</code>
        </aside>
      </div>
    </main>
  );
}
