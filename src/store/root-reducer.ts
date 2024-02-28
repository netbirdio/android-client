import {combineReducers} from 'redux';

import {reducer as appStatus} from './app-status';
import {reducer as appStatusPersist} from './app-status-persist';

export default combineReducers({
  appStatus,
  appStatusPersist,
});
