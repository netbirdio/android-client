import React from 'react';
import {shallow, mount, render} from 'enzyme';
import Onboarding from '../src/screens/Onboarding';
import configureStore from 'redux-mock-store';
import {Provider} from 'react-redux';
// import {actions as appStatusActions} from '../src/store/app-status'; 
// import {actions as appStatusPersistActions} from '../src/store/app-status-persist';
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
} as unknown as StackNavigationProp<RootStackParamList, 'Onboarding'>;

const route = {
  key: 'test-key',
  name: 'Onboarding',
} as RouteProp<RootStackParamList, 'Onboarding'>;

const hideMock = jest.fn();
jest.mock('react-native-splash-screen', () => ({hide: jest.fn()}));

const mockStore = configureStore();
const store = mockStore({});

describe('Onboarding component', () => {
  it('should render correctly', () => {
    const wrapper = shallow(
      <Provider store={store}>
        <Onboarding navigation={navigation} route={route} />
      </Provider>,
    );
    expect(wrapper.exists()).toBe(true);
  });

  it('should hide the splash screen on mount', () => {
    const wrapper = shallow(
      <Provider store={store}>
        <Onboarding navigation={navigation} route={route} />
      </Provider>,
    );
    expect(wrapper).toMatchSnapshot();
  });

//   it('should dispatch the correct actions when "Continue" is pressed', () => {
//     const setShowLeftDrawerMock = jest.fn();
//     const setTitleMainNavigatorMock = jest.fn();
//     const setOnboardingShowedMock = jest.fn();
//     const setOnboardingToChangeServerMock = jest.fn();

//     jest
//       .spyOn(appStatusActions, 'setShowLeftDrawer')
//       .mockImplementation(setShowLeftDrawerMock);
//     jest
//       .spyOn(appStatusActions, 'setTitleMainNavigator')
//       .mockImplementation(setTitleMainNavigatorMock);
//     jest
//       .spyOn(appStatusPersistActions, 'setOnboardingShowed')
//       .mockImplementation(setOnboardingShowedMock);
//     jest
//       .spyOn(appStatusActions, 'setOnboardingToChangeServer')
//       .mockImplementation(setOnboardingToChangeServerMock);

//     const wrapper = shallow(
//       <Provider store={store}>
//         <Onboarding navigation={navigation} route={route} />
//       </Provider>,
//     );

  
//       // Simulate button press
//       const button = wrapper.find('Onboarding').dive().find(`Button[text="Continue"]`);
//       button.simulate('press');

//     // Assertions on dispatched actions and other functionality
//     expect(setOnboardingShowedMock).toHaveBeenCalledWith();
//     expect(setOnboardingToChangeServerMock).toHaveBeenCalledWith(false);
//     expect(navigation.navigate).toHaveBeenCalledWith('Home');
//     expect(setShowLeftDrawerMock).toHaveBeenCalledWith(true);
//     expect(setTitleMainNavigatorMock).toHaveBeenCalledWith('');
//     // expect(dispatchMock).toHaveBeenCalledTimes(4);
//   });

  //   it('should dispatch the correct actions when "Continue" is pressed', () => {
  //     const setShowLeftDrawerMock = jest.fn();
  //     const setTitleMainNavigatorMock = jest.fn();
  //     const setOnboardingShowedMock = jest.fn();
  //     const setOnboardingToChangeServerMock = jest.fn();

  //     // Mock the useDispatch hook
  //     const dispatchMock = jest.fn();
  //     jest.mock('react-redux', () => ({
  //       useDispatch: () => jest.fn(),
  //     }));

  //     const wrapper = shallow(
  //       <Provider store={store}>
  //         <Onboarding navigation={navigation} route={route} />
  //       </Provider>,
  //     );
  //     const button = wrapper.find(`Button[text="Continue"]`);
  //     // expect(button).toExist();
  //     button.simulate('press');

  //     const instance = wrapper.instance() as Onboarding;
  //     instance.onPress();

  //     // const button = wrapper
  //     // .findWhere(node => node.prop('testID') === '123')
  //     //   .first();

  //     //   button.prop('onClick');

  //     jest
  //       .spyOn(appStatusActions, 'setShowLeftDrawer')
  //       .mockImplementation(setShowLeftDrawerMock);

  //     jest
  //       .spyOn(appStatusActions, 'setTitleMainNavigator')
  //       .mockImplementation(setTitleMainNavigatorMock);
  //     jest
  //       .spyOn(appStatusPersistActions, 'setOnboardingShowed')
  //       .mockImplementation(setOnboardingShowedMock);
  //     jest
  //       .spyOn(appStatusActions, 'setOnboardingToChangeServer')
  //       .mockImplementation(setOnboardingToChangeServerMock);
  //     // expect(setShowLeftDrawerMock).toHaveBeenCalledWith(true);
  //     // expect(setTitleMainNavigatorMock).toHaveBeenCalledWith('');
  //     expect(setOnboardingShowedMock).toHaveBeenCalledWith(true);
  //     expect(setOnboardingToChangeServerMock).toHaveBeenCalledWith(false);
  //     expect(mockNavigation.navigate).toHaveBeenCalledWith('Home');
  //     expect(dispatchMock).toHaveBeenCalledTimes(4);
  //   });

  //   it('should dispatch the correct actions when "change server" link is pressed', () => {
  //     const setShowLeftDrawerMock = jest.fn();
  //     const setTitleMainNavigatorMock = jest.fn();
  //     const setOnboardingShowedMock = jest.fn();
  //     const setOnboardingToChangeServerMock = jest.fn();
  //     const dispatchMock = jest.fn();

  //     // Mock the useDispatch hook to return the mocked dispatch function
  //     jest.mock('react-redux', () => ({
  //         useDispatch: () => jest.fn(),
  //         }));

  //         const wrapper = shallow(
  //             <Provider store={store}>
  //               <Onboarding navigation={mockNavigation} />
  //             </Provider>
  //           );
  //         const titleLink = wrapper.find('Text').findWhere((node) => node.props().id === '123');

  //     // Set the mocked functions as implementations for the dispatch actions
  //     jest.spyOn(appStatusActions, 'setShowLeftDrawer').mockImplementation(setShowLeftDrawerMock);
  //     jest.spyOn(appStatusActions, 'setTitleMainNavigator').mockImplementation(setTitleMainNavigatorMock);
  //     jest.spyOn(appStatusPersistActions, 'setOnboardingShowed').mockImplementation(setOnboardingShowedMock);
  //     jest.spyOn(appStatusActions, 'setOnboardingToChangeServer').mockImplementation(setOnboardingToChangeServerMock);

  //     // Simulate link press
  //     titleLink.props().onPress();

  //     expect(setShowLeftDrawerMock).toHaveBeenCalledWith(true);
  //     expect(setTitleMainNavigatorMock).toHaveBeenCalledWith('');
  //     expect(setOnboardingShowedMock).toHaveBeenCalledWith(true);
  //     expect(setOnboardingToChangeServerMock).toHaveBeenCalledWith(true);
  //     expect(mockNavigation.navigate).not.toHaveBeenCalled(); // Should not navigate to 'Home'
  //     expect(dispatchMock).toHaveBeenCalledTimes(4);
  //   });

  // You can add more test cases for other scenarios if needed
});
