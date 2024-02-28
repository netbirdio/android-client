import {ActionType, createAction} from 'typesafe-actions';

const actions = {
  setOnboardingShowed: createAction(
    'APP_STATUS_SET_ONBOARDING_SHOWED',
  )<boolean>(),
  writeDNS: createAction(
    'WRITE_DNS',
  )<string>(),
  writeIP: createAction(
    'WRITE_IP',
  )<string>(),
  setHomeModalServerInformationShowed: createAction(
    'APP_STATUS_SET_HOME_MODAL_SERVER_INFORMATION_SHOWED',
  )<boolean>(),
};

export type ActionTypes = ActionType<typeof actions>;
export default actions;
