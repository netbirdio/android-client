import React from 'react';
import {Platform} from 'react-native';
import {CardStyleInterpolators, createStackNavigator, TransitionPresets} from '@react-navigation/stack';
import {DefaultTheme, NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {RootStackParamList} from './RootStackParamList';
import {navigationRef} from './RootNavigation';

import LeftDrawer from '@components/LeftDrawer';
import Peers from '@screens/Peers';

const Stack = createStackNavigator<RootStackParamList>();

const MyTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    background: '#DDDDDD',
  },
};

// const isIOS = Platform.OS === 'ios';
// const Stack = isIOS ? createNativeStackNavigator<RootStackParamList>() : createStackNavigator<RootStackParamList>();
// const formSheet: any = isIOS
//   ? {presentation: 'formSheet'}
//   : {...TransitionPresets.ModalPresentationIOS, headerLeft: null, gestureEnabled: false};

const MainStackNavigator: React.FC = () => {
  return (
    <NavigationContainer theme={MyTheme} ref={navigationRef}>
      <Stack.Navigator
        screenOptions={{
          headerStyle: {
            elevation: 0,
            shadowOpacity: 0,
          },
        }}>
        <Stack.Screen
          options={{headerShown: false, title: ''}}
          name="LeftDrawer"
          component={LeftDrawer}
        />
        {/*<Stack.Screen*/}
        {/*  name="Peers"*/}
        {/*  options={{...formSheet}}*/}
        {/*  component={Peers}*/}
        {/*/>*/}
      </Stack.Navigator>
    </NavigationContainer>
  );
};

export default MainStackNavigator;
