import React from 'react';
import {
  CardStyleInterpolators,
  createStackNavigator,
  TransitionSpecs,
} from '@react-navigation/stack';

import Home from '../../screens/Home';
import Onboarding from '../../screens/Onboarding';
import ChangeServer from '@screens/ChangeServer';

import {colors} from '../../theme';
import Advance from '@screens/Advance';
import About from '@screens/About';
import Peers from '@screens/Peers';
import { useSelector } from "react-redux";
import { RootState } from "typesafe-actions";

const Stack = createStackNavigator();

export default ({}) => {
  const onboardingShowedState = useSelector(
    (state: RootState) => state.appStatusPersist.onboardingShowed,
  );

  return (
    <Stack.Navigator
      initialRouteName={onboardingShowedState ? 'Home' : 'Onboarding'}
      screenOptions={{
        headerStyle: {
          elevation: 0,
          shadowOpacity: 0,
        },
      }}>
      <Stack.Screen
        name="Onboarding"
        options={{headerShown: false, title: ''}}
        component={Onboarding}
      />
      <Stack.Screen
        name="Home"
        options={{headerShown: false, title: ''}}
        component={Home}
      />
      <Stack.Screen
        name="Advanced"
        options={{
          headerShown: false,
          title: 'Advanced',
          headerTintColor: colors.textDefault,
        }}
        component={Advance}
      />
      <Stack.Screen
        name="About"
        options={{
          headerShown: false,
          title: 'About',
          headerTintColor: colors.textDefault,
        }}
        component={About}
      />
      <Stack.Screen
        name="ChangeServer"
        options={{
          headerShown: false,
          title: 'Change Server',
          headerTintColor: colors.textDefault,
        }}
        component={ChangeServer}
      />
      {/*<Stack.Screen*/}
      {/*  name="Peers"*/}
      {/*  options={{*/}
      {/*    headerShown: true,*/}
      {/*    title: 'Peers',*/}
      {/*    headerTintColor: colors.textDefault,*/}
      {/*    headerLeft: props => <></>,*/}
      {/*    cardStyleInterpolator: CardStyleInterpolators.forVerticalIOS,*/}
      {/*  }}*/}
      {/*  component={Peers}*/}
      {/*/>*/}
    </Stack.Navigator>
  );
};
