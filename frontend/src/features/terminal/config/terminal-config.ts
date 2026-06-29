export type ConnectionStatus = 'Connecting' | 'Connected' | 'Disconnected' | 'Error';

export const terminalUserId = import.meta.env.VITE_USER_ID || 'anonymous';

export const defaultTerminalWsUrl = `ws://${window.location.hostname}:8080/ws/terminal`;

export const welcomeMessage = [
  'Linux Terminal Playground',
  'Commands run inside an isolated Ubuntu Docker container.',
  'Account: student',
  'Prompt: student@linux-terminal:~$',
  'Try: pwd, whoami, ls -al, mkdir test, cd test, echo hello',
  ''
];
