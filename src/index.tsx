import 'react-native-gesture-handler';
import React, { useEffect, useRef, useState } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import MainStackNavigator from '@navigation/MainStackNavigator';
import { PersistGate } from 'redux-persist/integration/react';
import { Provider, useDispatch, useSelector } from 'react-redux';
import { orderBy } from 'lodash';
import { store, persistor } from './store';
import { actions as appStatusActions } from './store/app-status';
import { ThemeProvider } from './theme/Theme';
import {
  changeServer,
  checkServer,
  prepare,
  setOnStarted,
  setOnError,
  setOnWebViewOpen,
  setOnConnected,
  setOnConnecting,
  setOnDisconnecting,
  setOnDisconnected,
  setOnPeersUpdate,
  setOnAddressChanged,
  setOnCustomTabClose,
  setPreSharedKey,
  inUsePreSharedKey,
  startService,
  switchConnection,
} from 'react-native-netbird-lib';
import { actions as appStatusPersistActions } from './store/app-status-persist';

import { RootState } from 'typesafe-actions';
import { Peer } from './store/app-status/types';

const StartVpn = () => {
  const dispatch = useDispatch();
  const connectionState = useSelector(state => state.appStatus.connection);
  const connectingRef = useRef(connectionState.connecting);
  connectingRef.current = connectionState.connecting;

  const changeServerState = useSelector(
    (state: RootState) => state.appStatus.changeServer,
  );

  const advanceState = useSelector(
    (state: RootState) => state.appStatus.advance,
  );

  const [timeoutSetPeers, setTimeoutSetPeers] = useState<any | null>(null);

  const resetConnectionState = (e: any) => {
    dispatch(
      appStatusActions.setConnection({
        requestedCancel: false,
        timeout: false,
        connected: false,
        connecting: false,
        disconnecting: false,
        requested: false,
        error: undefined,
        webView: undefined,
        ...e,
      }),
    );
  };

  useEffect(() => {
    if (!advanceState.requested) {
      return;
    }

    dispatch(appStatusActions.requestSaveAdvance(false));
    setPreSharedKey(advanceState.key).then((e: boolean) => {
      dispatch(
        appStatusActions.setAdvanceSaved({
          status: true,
          inUse: e,
        }),
      );
      if (connectionState.connected) switchConnection(false);
    });
  }, [advanceState.requested]);

  useEffect(() => {
    if (!connectionState.requested) {
      return;
    }
    switchConnection(!connectionState.connected);
  }, [connectionState.requested]);

  useEffect(() => {
    if (!connectionState.requestedCancel) {
      return;
    }
    switchConnection(false);
    resetConnectionState({});
  }, [connectionState.requestedCancel]);

  useEffect(() => {
    if (!changeServerState.requestCheck.status) {
      return;
    }
    checkServer(changeServerState.requestCheck.data.server).then((e: any) => {
      dispatch(appStatusActions.setChangeServerStatus(e));
      dispatch(
        appStatusActions.setPeers({
          total: 0,
          connected: 0,
          data: [],
        }),
      );
    });
  }, [changeServerState.requestCheck]);

  useEffect(() => {
    if (!changeServerState.requestChangeSetupKey.status) {
      return;
    }
    changeServer(
      changeServerState.requestChangeSetupKey.data.server,
      changeServerState.requestChangeSetupKey.data.setupKey,
    ).then((e: any) => {
      dispatch(appStatusActions.setChangeServerStatus(e));
      switchConnection(false);
    });
  }, [changeServerState.requestChangeSetupKey]);

  useEffect(() => {
    setOnConnected((e: any) => {
      console.log('onConnected ', e);
      onConnected();
    });

    setOnDisconnected((e: any) => {
      console.log('onDisconnected ', e);
      onDisconnected();
    });

    setOnStarted((e: any) => {
      console.log('onStarted ', e);
      onConnecting();
    });

    setOnConnecting((e: any) => {
      console.log('onConnecting ', e);
      onConnecting();
    });

    setOnDisconnecting((e: any) => {
      console.log('onDisconnecting ', e);
      onDisconnecting();
    });

    setOnError((e: any) => {
      console.log('onError', e);
      dispatch(
        appStatusActions.setWebViewError({
          error: e,
        }));
      connectionState.error?.message && onDisconnected();
    });

    setOnWebViewOpen((e: any) => {
      dispatch(
        appStatusActions.setWebViewOpen({
          webView: e,
        }));
    });

    setOnPeersUpdate((e: string | any[]) => {
      if (timeoutSetPeers === null) {
        const t = setTimeout(function () {
          dispatch(
            appStatusActions.setPeers({
              total: e.length,
              connected: (e as Peer[]).filter(
                p => p.connStatus.toLowerCase() === 'connected',
              ).length,
              data: orderBy(e, ['domainName'], ['asc']),
            }),
          );
          setTimeoutSetPeers(null);
        }, 1000);
        setTimeoutSetPeers(t);
      }
    });

    setOnAddressChanged((e: { domainName: string; ip: any; }) => {
      console.log('onAddressChanged', e);
      if (e.domainName !== '') {
        dispatch(appStatusPersistActions.writeDNS(e.domainName))
        dispatch(appStatusPersistActions.writeIP(e.ip))
        dispatch(
          appStatusActions.setConnectionAddress({
            domainName: e.domainName,
            ip: e.ip,
          }),
        );
      }
    });

    setOnCustomTabClose((e: any) => {
      console.log('onCustomTabClose', e);
      setTimeout(() => {
        console.log('connecting', connectingRef);
        if (!connectingRef.current) {
          return;
        }
        dispatch(appStatusActions.setConnectionTimeout(true));
      }, 20000);
    });

    prepare()
      .then(() => {
        console.log('prepared');
      })
      .catch((err: Error) => {
        // only happen on android when activity is not running yet
        console.log(err);
        prepare();
      });

    inUsePreSharedKey()
      .then((inUse: boolean) => {
        console.log('inUsePreSharedKey', inUse);
        dispatch(appStatusActions.setAdvanceInUse(inUse));
      })
      .catch((err: Error) => {
        console.log('inUsePreSharedKey', err);
      });

    startService();
  }, []);

  const onConnecting = () => {
    dispatch(appStatusActions.setConnecting(true));
  };

  const onDisconnecting = () => {
    dispatch(appStatusActions.setDisconnecting(true));
  };

  const onConnected = () => {
    dispatch(appStatusActions.setConnectionStatus(true));
  };

  const onDisconnected = () => {
    dispatch(appStatusActions.setConnectionStatus(false));
    dispatch(
      appStatusActions.setPeers({
        total: 0,
        connected: 0,
        data: [],
      }),
    );
  };

  return <></>;
};

const App = () => {
  return (
    <Provider store={store}>
      <PersistGate loading={null} persistor={persistor}>
        <StartVpn />
        <SafeAreaProvider>
          <ThemeProvider>
            <MainStackNavigator />
          </ThemeProvider>
        </SafeAreaProvider>
      </PersistGate>
    </Provider>
  );
};

export default App;
