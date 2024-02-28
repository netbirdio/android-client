import {ActionType, createAction} from 'typesafe-actions';
import {
  Advance,
  ChangeServer,
  ChangeServerData,
  ChangeServerRequest,
  Connection,
  Peer,
  PeerList,
} from './types';
import {ChangeServerStatusCode} from 'react-native-netbird-lib';

const actions = {
  setOnboardingToChangeServer: createAction(
    'APP_STATUS_SET_ONBOARDING_TO_CHANGE_SERVER',
  )<boolean>(),
  setShowLeftDrawer: createAction('APP_STATUS_SET_SHOW_LEFT_DRAWER')<boolean>(),
  setTitleMainNavigator: createAction(
    'APP_STATUS_SET_TITLE_MAIN_NAVIGATOR',
  )<string>(),
  setConnection: createAction('APP_STATUS_SET_CONNECTION')<Connection>(),
  setConnectionStatus: createAction(
    'APP_STATUS_SET_CONNECTION_STATUS',
  )<boolean>(),
  setConnecting: createAction('APP_STATUS_SET_CONNECTING')<boolean>(),
  setDisconnecting: createAction('APP_STATUS_SET_DISCONNECTING')<boolean>(),
  requestConnection: createAction('APP_STATUS_REQUEST_CONNECTION')<boolean>(),
  requestCancelConnection: createAction(
    'APP_STATUS_REQUEST_CANCEL_CONNECTION',
  )<boolean>(),
  setConnectionTimeout: createAction(
    'APP_STATUS_SET_CONNECTION_TIMEOUT',
  )<boolean>(),
  setConnectionAddress: createAction('APP_STATUS_SET_ADDRESS')<{
    domainName: string;
    ip: string;
  }>(),
  setWebViewError: createAction('APP_STATUS_ERROR')<{
    error: any;
  }>(),
  setWebViewOpen: createAction('APP_STATUS_WEB_VIEW_OPEN')<{
    webView: any;
  }>(),

  setAdvance: createAction('APP_STATUS_SET_ADVANCE')<Advance>(),
  requestSaveAdvance: createAction(
    'APP_STATUS_REQUEST_SAVE_ADVANCE',
  )<boolean>(),
  setAdvanceSaving: createAction('APP_STATUS_SET_ADVANCE_SAVING')<boolean>(),
  setAdvanceSaved: createAction('APP_STATUS_SET_ADVANCE_SAVED')<{
    status: boolean;
    inUse: boolean;
  }>(),
  setAdvanceInUse: createAction('APP_STATUS_SET_ADVANCE_IN_USE')<boolean>(),

  askReset: createAction('APP_STATUS_SET_ASK_RESET')<boolean>(),
  confirmReset: createAction('APP_STATUS_SET_CONFIRM_RESET')<boolean>(),

  setChangeServer: createAction('APP_STATUS_SET_CHANGE_SERVER')<ChangeServer>(),
  resetChangeServer: createAction('APP_STATUS_RESET_CHANGE_SERVER')(),
  askChangeServer: createAction('APP_STATUS_ASK_CHANGE_SERVER')<boolean>(),
  requestCheckServer: createAction(
    'APP_STATUS_REQUEST_CHECK_SERVER',
  )<ChangeServerRequest>(),
  requestChangeServerSetupKey: createAction(
    'APP_STATUS_REQUEST_CHANGE_SERVER_SETUP_KEY',
  )<ChangeServerRequest>(),
  setChangeServerChecking: createAction(
    'APP_STATUS_SET_CHECKING_SERVER',
  )<boolean>(),
  setChangeServerChangingSetupKey: createAction(
    'APP_STATUS_SET_CHANGING_SERVER_SETUP_KEY',
  )<boolean>(),
  setChangeServerStatus: createAction(
    'APP_STATUS_SET_CHANGE_SERVER_STATUS',
  )<ChangeServerStatusCode>(),
  setChangeServerData: createAction(
    'APP_STATUS_SET_CHANGE_SERVER_DATA',
  )<ChangeServerData>(),

  setPeers: createAction('APP_STATUS_SET_PEERS')<PeerList>(),
};

export type ActionTypes = ActionType<typeof actions>;
export default actions;
