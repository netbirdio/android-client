import React from 'react';
import {shallow} from 'enzyme';
import {Provider} from 'react-redux';
import About from '../src/screens/About/index';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackNavigationProp} from '@react-navigation/stack';
import {RouteProp} from '@react-navigation/native';
import configureStore from 'redux-mock-store';

const mockStore = configureStore();
const initialState = {}; 
const store = mockStore(initialState);

const navigation = {
  navigate: jest.fn(),
  goBack: jest.fn(),
  dispatch: jest.fn(),
  reset: jest.fn(),
  isFocused: jest.fn(),
  canGoBack: jest.fn(),
} as unknown as StackNavigationProp<RootStackParamList, 'About'>;

const route: RouteProp<RootStackParamList, 'About'> = {
  key: 'mock-key',
  name: 'About',
};

// Mock the Linking.openURL method
jest.mock('react-native', () => ({
  ...jest.requireActual('react-native'),
  Linking: {
    openURL: jest.fn(),
  },
}));

jest.mock('react-native-device-info', () => ({
  getVersion: jest.fn(() => '1.0.1'), // Mock the getVersion function to return a specific value
}));

describe('About Screen', () => {
  it('displays the correct version', () => {
    const wrapper = shallow(
      <Provider store={store}>
        <About navigation={navigation} route={route} />
      </Provider>,
    );
    const textComponents = wrapper.find('Text');

    expect(textComponents.at(0).contains('Version'));
    const versionText = wrapper.findWhere(
      node => node.prop('testID') === 'versionText',
    );
    expect(versionText).toBeDefined();
    expect(textComponents.at(0).contains('1.0.1'));
    expect(textComponents.at(1).contains('License agreement'));
    expect(textComponents.at(2).contains('Privacy policy'));
    expect(textComponents.at(3).contains('© 2023 NetBird all rights reserved'));
    expect(wrapper).toMatchSnapshot();
  });

});
