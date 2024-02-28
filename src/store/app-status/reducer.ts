import {createReducer} from 'typesafe-actions';
import {combineReducers} from 'redux';
import actions, {ActionTypes} from './actions';
import {
  Advance,
  ChangeServer,
  Connection,
  PeerList,
  Reset,
} from './types';

type StateType = Readonly<{
  showLeftDrawer: boolean;
  titleMainNavigator: string;
  connection: Connection;
  advance: Advance;
  reset: Reset;
  changeServer: ChangeServer;
  peers: PeerList;
  onboardingToChangeServer: boolean;
}>;

const initialState: StateType = {
  showLeftDrawer: false,
  titleMainNavigator: '',
  connection: {
    connected: false,
    connecting: false,
    disconnecting: false,
    requested: false,
    requestedCancel: false,
    webView: undefined,
    error: undefined,
    timeout: false,
    domainName: '',
    ip: '',
  },
  advance: {
    requested: false,
    key: '',
    inUse: false,
    saving: false,
    saved: false,
    error: false,
  },
  reset: {
    askReset: false,
    confirmReset: false,
  },
  changeServer: {
    status: 0,
    message: null,
    ask: false,
    checking: false,
    requestCheck: {
      status: false,
      data: {
        server: '',
        setupKey: '',
      },
    },
    changing: false,
    requestChangeSetupKey: {
      status: false,
      data: {
        server: '',
        setupKey: '',
      },
    },
    data: {
      server: '',
      setupKey: '',
    },
  },
  peers: {
    total: 0,
    connected: 0,
    data: [],
  },
  onboardingToChangeServer: false,
};

const onboardingToChangeServer = createReducer<boolean, ActionTypes>(
  initialState.onboardingToChangeServer,
).handleAction(
  actions.setOnboardingToChangeServer,
  (_, action) => action.payload,
);

const showLeftDrawerNavigation = createReducer<boolean, ActionTypes>(
  initialState.showLeftDrawer,
).handleAction(actions.setShowLeftDrawer, (_, action) => action.payload);

const titleMainNavigator = createReducer<string, ActionTypes>(
  initialState.titleMainNavigator,
).handleAction(actions.setTitleMainNavigator, (_, action) => action.payload);

const connection = createReducer<Connection, ActionTypes>(
  initialState.connection,
)
  .handleAction(actions.setConnection, (_, action) => action.payload)
  .handleAction(actions.setConnectionStatus, (_, action) => ({
    ..._,
    connected: action.payload,
    connecting: false,
    disconnecting: false,
    requested: false,
    requestedCancel: false,
    timeout: false,
  }))
  .handleAction(actions.setConnecting, (_, action) => ({
    ..._,
    connecting: action.payload,
    disconnecting: false,
  }))
  .handleAction(actions.setDisconnecting, (_, action) => ({
    ..._,
    connecting: false,
    disconnecting: action.payload,
  }))
  .handleAction(actions.requestConnection, (_, action) => ({
    ..._,
    requested: action.payload,
  }))
  .handleAction(actions.setConnectionTimeout, (_, action) => ({
    ..._,
    timeout: action.payload,
  }))
  .handleAction(actions.requestCancelConnection, (_, action) => ({
    ..._,
    requestedCancel: action.payload,
  }))
  .handleAction(actions.setConnectionAddress, (_, action) => ({
    ..._,
    domainName: action.payload.domainName,
    ip: action.payload.ip,
  }))
  .handleAction(actions.setWebViewError, (_, action) => ({
    ..._,
    error: action.payload.error,
  }))
  .handleAction(actions.setWebViewOpen, (_, action) => ({
    ..._,
    webView: action.payload.webView,
  }));

const advance = createReducer<Advance, ActionTypes>(initialState.advance)
  .handleAction(actions.setAdvance, (_, action) => ({
    ...action.payload,
    inUse: _.inUse,
  }))
  .handleAction(actions.requestSaveAdvance, (_, action) => ({
    ..._,
    requested: action.payload,
  }))
  .handleAction(actions.setAdvanceSaving, (_, action) => ({
    ..._,
    saving: action.payload,
  }))
  .handleAction(actions.setAdvanceSaved, (_, action) => ({
    ..._,
    saving: false,
    saved: action.payload.status,
    key: '',
    inUse: action.payload.inUse,
  }))
  .handleAction(actions.setAdvanceInUse, (_, action) => ({
    ..._,
    inUse: action.payload,
  }));

const reset = createReducer<Reset, ActionTypes>(initialState.reset)
  .handleAction(actions.askReset, (_, action) => ({
    ..._,
    askReset: action.payload,
    confirmReset: false,
  }))
  .handleAction(actions.confirmReset, (_, action) => ({
    ..._,
    askReset: false,
    confirmReset: action.payload,
  }));

const changeServer = createReducer<ChangeServer, ActionTypes>(
  initialState.changeServer,
)
  .handleAction(actions.setChangeServer, (_, action) => action.payload)
  .handleAction(actions.resetChangeServer, _ => ({
    ...initialState.changeServer,
  }))
  .handleAction(actions.askChangeServer, (_, action) => ({
    ..._,
    ask: action.payload,
  }))
  .handleAction(actions.requestCheckServer, (_, action) => ({
    ..._,
    requestCheck: action.payload,
    data: {...action.payload.data},
    checking: true,
    changing: false,
  }))
  .handleAction(actions.requestChangeServerSetupKey, (_, action) => ({
    ..._,
    requestChangeSetupKey: action.payload,
    data: {...action.payload.data},
    checking: false,
    changing: true,
  }))
  .handleAction(actions.setChangeServerChecking, (_, action) => ({
    ..._,
    checking: action.payload,
  }))
  .handleAction(actions.setChangeServerChangingSetupKey, (_, action) => ({
    ..._,
    changing: action.payload,
  }))
  .handleAction(actions.setChangeServerStatus, (_, action) => ({
    ..._,
    status: action.payload,
    checking: false,
    changing: false,
  }))
  .handleAction(actions.setChangeServerData, (_, action) => ({
    ..._,
    data: action.payload,
  }));

const peers = createReducer<PeerList, ActionTypes>(
  initialState.peers,
).handleAction(actions.setPeers, (_, action) => action.payload);

export default combineReducers({
  onboardingToChangeServer,
  showLeftDrawerNavigation,
  titleMainNavigator,
  connection,
  advance,
  reset,
  changeServer,
  peers,
});
