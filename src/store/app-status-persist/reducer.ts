import { createReducer } from 'typesafe-actions';
import { combineReducers } from 'redux';
import actions, { ActionTypes } from './actions';

type StateType = Readonly<{
  onboardingShowed: boolean;
  homeModalServerInformationShowed: boolean;
  dns: string;
  ip: string;
}>;

const initialState: StateType = {
  onboardingShowed: false,
  homeModalServerInformationShowed: false,
  dns: '',
  ip: '',
};

const onboardingShowed = createReducer<boolean, ActionTypes>(
  initialState.onboardingShowed,
).handleAction(actions.setOnboardingShowed, (_, action) => action.payload);

const writeDNS = createReducer<string, ActionTypes>(
  initialState.dns,
).handleAction(actions.writeDNS, (_, action) => action.payload);

const writeIP = createReducer<string, ActionTypes>(
  initialState.ip,
).handleAction(actions.writeIP, (_, action) => action.payload);

const homeModalServerInformationShowed = createReducer<boolean, ActionTypes>(
  initialState.homeModalServerInformationShowed,
).handleAction(
  actions.setHomeModalServerInformationShowed,
  (_, action) => action.payload,
);

export default combineReducers({
  onboardingShowed,
  homeModalServerInformationShowed,
  writeDNS,
  writeIP
});
