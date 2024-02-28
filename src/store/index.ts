import { applyMiddleware, legacy_createStore as createStore } from 'redux';
import {persistStore, persistReducer} from 'redux-persist';
import AsyncStorage from '@react-native-community/async-storage';
import createSagaMiddleware from 'redux-saga';

import {sagas as appStatusSagas} from './app-status';

const persistConfig = {
  key: 'netbird-persist',
  storage: AsyncStorage,
  whitelist: ['appStatusPersist'],
};

import rootReducer from './root-reducer';

const sagaMiddleware = createSagaMiddleware();
const middlewares = [sagaMiddleware];

const enhancer = applyMiddleware(...middlewares);

const persistedReducer = persistReducer(persistConfig, rootReducer);

const store = createStore(persistedReducer, enhancer);
const persistor = persistStore(store);

sagaMiddleware.run(appStatusSagas);

export {rootReducer, store, persistor};
