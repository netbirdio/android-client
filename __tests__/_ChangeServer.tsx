import React from 'react';
import { shallow } from 'enzyme';
import configureStore from 'redux-mock-store';
import { Provider } from 'react-redux';
import ChangeServer  from '../src/screens/ChangeServer/index'; 
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackNavigationProp} from '@react-navigation/stack';
import {RouteProp} from '@react-navigation/native';

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
const store = mockStore({
  appStatus: {
    changeServer: {
      status: 0,
      checking: false,
      changing: false,
      data: { server: '', setupKey: '' },
    },
  },
});

describe('ChangeServer component', () => {
  it('should render correctly', () => {
    const wrapper = shallow(
      <Provider store={store}>
        <ChangeServer navigation={navigation} route={route} />
      </Provider>
    );
    expect(wrapper).toMatchSnapshot();
  });

  // it('should update dataServer state on server input change', () => {
  //   const wrapper = shallow(
  //     <Provider store={store}>
  //       <ChangeServer navigation={navigation} route={route} />
  //     </Provider>
  //   );
  //   const input = wrapper.find('Input').first();
  //   const serverValue = 'https://example-api.domain.com';
  //   input.simulate('changeText', serverValue);

  //   // Retrieve the component instance
  //   const instance = wrapper.find('ChangeServer').dive().instance();
    
  //   // Check if dataServer state has been updated
  //   expect(instance.state.dataServer.server).toEqual(serverValue);
  //   expect(instance.state.error).toBe(false);
  // });

  // it('should show error message on invalid server input', () => {
  //   const wrapper = shallow(
  //     <Provider store={store}>
  //       <ChangeServer navigation={navigation} route={route} />
  //     </Provider>
  //   );
  //   const input = wrapper.find('Input').first();
  //   const invalidServer = 'invalid-server';
  //   input.simulate('changeText', invalidServer);

  //   // Retrieve the component instance
  //   const instance = wrapper.find('ChangeServer').dive().instance();

  //   // Check if dataServer state has been updated
  //   expect(instance.state.dataServer.server).toEqual(invalidServer);
  //   expect(instance.state.error).toBe(true);

  //   // Verify the error message displayed on the input
  //   const errorMessage = wrapper.find({ testID: 'server-error-message' });
  //   expect(errorMessage).toHaveLength(1);
  //   expect(errorMessage.props().visible).toBe(true);
  //   expect(errorMessage.props().children).toEqual('Ivalid server address');
  // });

  // Test other interactions and dispatched actions as needed.
});
