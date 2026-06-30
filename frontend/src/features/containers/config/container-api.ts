export const containerApiBaseUrl = import.meta.env.VITE_API_BASE_URL || `http://${window.location.hostname}:8080`;

export const userId = import.meta.env.VITE_USER_ID || 'anonymous';

export const allowedVolumeHostPathBase = import.meta.env.VITE_ALLOWED_VOLUME_HOST_PATH_BASE || '/mnt/storage';
