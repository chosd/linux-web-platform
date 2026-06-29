export function messageForClose(event: CloseEvent) {
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
