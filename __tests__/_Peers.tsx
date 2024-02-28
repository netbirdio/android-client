import React from 'react';
import {ShallowWrapper, shallow} from 'enzyme';
import Peers from '../src/screens/Peers/index';
import ListPeers from '@components/ListPeers';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackNavigationProp} from '@react-navigation/stack';
import {RouteProp} from '@react-navigation/native';
import { Provider } from 'react-redux';
import configureStore from 'redux-mock-store';

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

describe('Peers component', () => {
    let wrapper: ShallowWrapper;
   // Create a mock Redux store
   const mockStore = configureStore([]);
   const store = mockStore({}); // You can pass your initial state here
 
   beforeEach(() => {
     // Mount the component with the mock Redux store
     wrapper = shallow(
       <Provider store={store}>
         <Peers navigation={navigation} route={route} />
       </Provider>
     );
   });
  
    it('should render the ListPeers component', () => {
      // Check if the ListPeers component is rendered within the Peers component
      expect(wrapper.find(ListPeers)).toMatchSnapshot();
    });
  
    it('should dispatch actions when focused and unfocused', () => {

      const mockDispatch = jest.fn();
      jest.mock('react-redux', () => ({
        useDispatch: () => mockDispatch,
      }));
  
      // Mount the component
      wrapper = shallow(
        <Provider store={store}>
          <Peers navigation={navigation} route={route} />
        </Provider>
      );
      wrapper.unmount();
      expect(mockDispatch).toMatchSnapshot();
    });
  
  });
  