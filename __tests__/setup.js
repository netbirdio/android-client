import Enzyme from 'enzyme';
import Adapter from '@cfaester/enzyme-adapter-react-18';
import { NativeModules } from 'react-native';
import mockAsyncStorage from '@react-native-community/async-storage/jest/async-storage-mock';
import configureStore from 'redux-mock-store';

jest.mock('react-native-splash-screen', () => ({
  hide: jest.fn(),
  show: jest.fn(),
}));
jest.mock('react-native-inappbrowser-reborn', () => ({
  open: jest.fn(),
  close: jest.fn(),
}));

jest.mock('react-native-material-menu', () => {
  const React = require('react');
  const { View } = require('react-native');

  const Menu = ({ children }) => <View>{children}</View>;
  const MenuDivider = () => <View />;
  const MenuItem = ({ children }) => <View>{children}</View>;

  return {
    Menu,
    MenuDivider,
    MenuItem,
  };
});

jest.mock('@react-native-clipboard/clipboard', () => {
  const Clipboard = {
    setString: jest.fn(),
  };

  return Clipboard;
});

jest.mock('@react-native-community/async-storage', () => mockAsyncStorage);
jest.mock('@gorhom/bottom-sheet', () => ({
  __esModule: true,
}));

jest.mock('react-native', () => {
  const Platform = {
    OS: 'ios',
    select: jest.fn(),
    Version: 11.0,
  };

  const NativeModules = {
    NetbirdLib: {
      multiply: jest.fn(),
      prepare: jest.fn(),
    },
  };

  const Settings = {
    settings: {},
  };

  const StyleSheet = {
    create: jest.fn(style => style),
  };

  return {
    Platform,
    NativeModules,
    Settings,
    StyleSheet,
  };
});

jest.mock('react-native-netbird-lib', () => ({
  __esModule: true,
  default: {
    multiply: jest.fn(),
    prepare: jest.fn(),
    Platform: {
      select: jest.fn().mockReturnValue(''), // Adjust the return value as needed
    },
  },
}));

jest.mock('react-native-netbird-lib/node_modules/react-native/index');
jest.mock('lottie-react-native', () => {
  const mockLottie = jest.fn().mockReturnValue(null);

  mockLottie.prototype.View = jest.fn().mockReturnValue(null);
  mockLottie.prototype.setAnimation = jest.fn();
  return mockLottie;
});

jest.mock('react-native-device-info', () => {
  return {
    getVersion: jest.fn(() => '1.0.0'),
    getDeviceType: jest.fn(() => 'Tablet'),
  };
});

Enzyme.configure({ adapter: new Adapter() });
require('react-native-mock-render/mock');

NativeModules.RNCNetInfo = {
  getCurrentState: jest.fn(() => Promise.resolve()),
  addListener: jest.fn(),
  removeListeners: jest.fn()
};
  // Suppress the warning message
  console.error = jest.fn();
