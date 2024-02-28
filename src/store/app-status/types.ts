import {ChangeServerStatusCode} from 'react-native-netbird-lib';

export interface CustomError {
  status?: boolean;
  message?: string;
}

export interface Connection {
  connected: boolean;
  connecting: boolean;
  disconnecting: boolean;
  requested: boolean;
  requestedCancel: boolean;
  error?: CustomError;
  webView?: string | boolean;
  timeout: boolean;
  domainName: string;
  ip: string;
}

export interface Advance {
  requested: boolean;
  key: string;
  inUse: boolean;
  saving: boolean;
  saved: boolean;
  error: boolean;
}

export interface Reset {
  askReset: boolean;
  confirmReset: boolean;
}

export interface ChangeServerData {
  server: string;
  setupKey: string;
}

export interface ChangeServerRequest {
  status: boolean;
  data: ChangeServerData;
};

export interface ChangeServer {
  message?: string | null;
  status?: ChangeServerStatusCode|null;
  ask: boolean;
  requestCheck: ChangeServerRequest;
  requestChangeSetupKey: ChangeServerRequest;
  checking: boolean;
  changing: boolean;
  data: ChangeServerData;
}

export interface Peer {
  domainName: string;
  ip: string;
  connStatus: string;
}

export interface PeerList {
  total: number;
  connected: number;
  data: Peer[];
}
