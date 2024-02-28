import React from 'react';
import 'react-native';
import configureStore from 'redux-mock-store';
import { shallow } from 'enzyme';
import { Provider } from 'react-redux';
import { RootStackParamList } from '@navigation/RootStackParamList';
import { StackNavigationProp } from '@react-navigation/stack';
import { RouteProp } from '@react-navigation/native';
import Home from '../src/screens/Home/index';

const navigation = {
  navigate: jest.fn(),
  goBack: jest.fn(),
  dispatch: jest.fn(),
  reset: jest.fn(),
  isFocused: jest.fn(),
  canGoBack: jest.fn(),
} as unknown as StackNavigationProp<RootStackParamList, 'Home'>;

const route = {
  key: 'test-key',
  name: 'Home',
} as RouteProp<RootStackParamList, 'Home'>;

const mockStore = configureStore();
const store = mockStore({}); 

describe('Home Screen', () => {
  it('renders correctly', () => {
    const wrapper = shallow(
      <Provider store={store}>
        <Home navigation={navigation} route={route} />
      </Provider>
    );
    expect(wrapper).toMatchSnapshot();
  });
});
