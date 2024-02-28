import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-netbird-lib' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const NetbirdLib = NativeModules.NetbirdLib
  ? NativeModules.NetbirdLib
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

function setEmitter(name: string, fn: (e: any) => void) {
  const eventEmitter = new NativeEventEmitter();
  NetbirdLib.eventListener = eventEmitter.addListener(name, (event) => {
    fn(event);
  });
}

export function setOnStarted(fn: (e: any) => void) {
  setEmitter('onStarted', fn);
}

export function setOnStopped(fn: (e: any) => void) {
  setEmitter('onStopped', fn);
}

export function setOnError(fn: (e: any) => void) {
  setEmitter('onError', fn);
}

export function setOnWebViewOpen(fn: (e: any) => void) {
  setEmitter('onWebViewOpen', fn);
}

export function setOnConnected(fn: (e: any) => void) {
  setEmitter('onConnected', fn);
}

export function setOnDisconnected(fn: (e: any) => void) {
  setEmitter('onDisconnected', fn);
}

export function setOnConnecting(fn: (e: any) => void) {
  setEmitter('onConnecting', fn);
}

export function setOnDisconnecting(fn: (e: any) => void) {
  setEmitter('onDisconnecting', fn);
}

export function setOnPeersUpdateCount(fn: (e: any) => void) {
  setEmitter('onPeersUpdateCount', fn);
}

export function setOnPeersUpdate(fn: (e: any) => void) {
  setEmitter('onPeersUpdate', fn);
}

export function setOnAddressChanged(fn: (e: any) => void) {
  setEmitter('onAddressChanged', fn);
}

export function setOnCustomTabClose(fn: (e: any) => void) {
  setEmitter('onCustomTabClose', fn);
}

export function multiply(a: number, b: number): Promise<number> {
  return NetbirdLib.multiply(a, b);
}

export const prepare = NetbirdLib.prepare;

export const startService = NetbirdLib.startService;

export const switchConnection = NetbirdLib.switchConnection;

//export const checkServer = NetbirdLib.checkServer;
export function checkServer(server: string): Promise<ChangeServerStatusCode> {
  return NetbirdLib.checkServer(server);
}

export function setPreSharedKey(key: string): Promise<boolean> {
  return NetbirdLib.setPreSharedKey(key);
}

export function inUsePreSharedKey(): Promise<boolean> {
  return NetbirdLib.inUsePreSharedKey();
}

export function isTraceLogEnabled(): Promise<boolean> {
  return NetbirdLib.isTraceLogEnabled();
}

export const enableTraceLog = NetbirdLib.enableTraceLog;

export const disableTraceLog = NetbirdLib.disableTraceLog;

//export const changeServer = NetbirdLib.changeServer;
export function changeServer(
  server: string,
  setupKey: string
): Promise<ChangeServerStatusCode> {
  return NetbirdLib.changeServer(server, setupKey);
}

export enum ChangeServerStatusCode {
  NONE,
  SSO_IS_SUPPORTED,
  SSO_IS_NOT_SUPPORTED,
  CHECK_ERROR,
  CHANGED,
  CHANGED_ERROR,
}
