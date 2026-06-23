import { useCallback, useEffect, useRef, useState } from 'react';
import { Terminal } from '@xterm/xterm';
import { FitAddon } from '@xterm/addon-fit';
import '@xterm/xterm/css/xterm.css';

type ConnectionStatus = 'Connecting' | 'Connected' | 'Disconnected' | 'Error';

const defaultWsUrl = `ws://${window.location.hostname}:8080/ws/terminal`;
const userId = import.meta.env.VITE_USER_ID || 'anonymous';
const welcomeMessage = [
  'Linux Terminal Playground',
  'Commands run inside an isolated Ubuntu Docker container.',
  'Account: student',
  'Prompt: student@linux-terminal:~$',
  'Try: pwd, whoami, ls -al, mkdir test, cd test, echo hello',
  ''
];

function messageForClose(event: CloseEvent) {
  if (event.reason.includes('Docker could not start')) {
    return 'Docker could not start the terminal container. Build the image and check Docker daemon access.';
  }
  if (event.reason.includes('Terminal server could not start Docker')) {
    return 'The terminal server could not start Docker. Check Docker daemon access on the backend host.';
  }
  if (event.reason.includes('Idle timeout')) {
    return 'Idle timeout reached. Reconnect to start a new terminal.';
  }
  if (event.reason.includes('container process exited')) {
    return event.code === 1000 ? 'Terminal session ended.' : 'Terminal process exited unexpectedly.';
  }
  if (event.reason) {
    return event.reason;
  }
  return 'Connection closed.';
}

type TerminalViewProps = {
  containerName: string;
  displayName: string;
  onBack: () => void;
};

export function TerminalView({ containerName, displayName, onBack }: TerminalViewProps) {
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

    const baseWsUrl = import.meta.env.VITE_TERMINAL_WS_URL || defaultWsUrl;
    const wsUrl = `${baseWsUrl}?userId=${encodeURIComponent(userId)}&containerName=${encodeURIComponent(containerName)}`;
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
    <main className="terminal-page">
      <header className="terminal-toolbar">
        <div>
          <h1>Linux Terminal Playground</h1>
          <div className="terminal-subtitle">{displayName}</div>
          <div className="connection-row">
            <span className={`status status-${status.toLowerCase()}`}>{status}</span>
            <span className="status-message">{statusMessage}</span>
          </div>
        </div>
        <div className="terminal-actions">
          <button type="button" onClick={onBack}>Dashboard</button>
          <button type="button" onClick={connect} disabled={status === 'Connecting'}>
            Reconnect
          </button>
        </div>
      </header>
      <div className="terminal-workspace">
        <section className="terminal-shell" aria-label="Linux terminal">
          <div ref={terminalElementRef} className="terminal-host" />
        </section>
        <aside className="usage-panel" aria-label="Basic terminal usage">
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
