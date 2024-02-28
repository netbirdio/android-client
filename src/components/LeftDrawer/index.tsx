import React from 'react';
import {DrawerActions, useNavigation} from '@react-navigation/native';
import {
  Alert,
  Image,
  Linking,
  Pressable,
  StyleSheet,
  Text,
  View,
  Dimensions,
} from 'react-native';
import {
  createDrawerNavigator,
  DrawerContentComponentProps,
  DrawerContentScrollView,
  DrawerItem,
} from '@react-navigation/drawer';
import MainStack from './MainStack';
import {useDispatch, useSelector} from 'react-redux';
import {RootState} from 'typesafe-actions';
import DeviceInfo from 'react-native-device-info';
import {actions as appStatusActions} from '../../store/app-status';
import {colors} from '../../theme';

const windowWidth = Dimensions?.get('window').width;
const windowHeight = Dimensions?.get('window').height;
const Drawer = createDrawerNavigator();
var version = DeviceInfo.getVersion();

export default () => {
  const dispatch = useDispatch();
  const showLeftDrawer = useSelector(
    (state: RootState) => state.appStatus.showLeftDrawerNavigation,
  );
  const titleMainNavigator = useSelector(
    (state: RootState) => state.appStatus.titleMainNavigator,
  );

  const navigation = useNavigation();

  return (
    <Drawer.Navigator
      drawerContent={props => <CustomDrawerComp {...props} />}
      screenOptions={{
        gestureHandlerProps: {
          enabled: titleMainNavigator === '',
        },
        headerStyle: {
          elevation: 0,
          shadowOpacity: 0,
          backgroundColor:
            titleMainNavigator === ''
              ? colors.bgHeaderColor
              : colors.navBarColor,
        },
        drawerStyle: {
          borderTopRightRadius: 18,
        },
      }}>
      <Drawer.Screen
        name="MainStack"
        options={{
          title: '',
          headerShown: showLeftDrawer,
          headerLeft: props => {
            return (
              <Pressable
                android_ripple={{
                  color: '#F68330',
                  foreground: true,
                  borderless: true,
                }}
                style={{marginLeft: 15}}
                hitSlop={{top: 25, bottom: 25, left: 25, right: 25}}
                onPress={() => {
                  if (titleMainNavigator === '') {
                    return navigation.dispatch(DrawerActions.toggleDrawer());
                  }
                  dispatch(appStatusActions.setTitleMainNavigator(''));
                  dispatch(appStatusActions.setShowLeftDrawer(true));
                  navigation.goBack();
                }}>
                {titleMainNavigator === '' ? (
                  <>
                    <Image
                      style={{
                        paddingLeft: 18,
                        width: 15,
                        marginBottom: 0.01,
                        resizeMode: 'contain',
                      }}
                      source={require('../../assets/images/line.png')}
                    />
                    <Image
                      style={{
                        paddingLeft: 18,
                        width: 15,
                        marginBottom: 0.1,
                        resizeMode: 'contain',
                      }}
                      source={require('../../assets/images/line.png')}
                    />
                    <Image
                      style={{
                        paddingLeft: 18,
                        width: 15,
                        marginBottom: 0.1,
                        resizeMode: 'contain',
                      }}
                      source={require('../../assets/images/line.png')}
                    />
                  </>
                ) : (
                  <View style={styles.titleContainer}>
                    <Image
                      style={{marginRight: 15}}
                      source={require('../../assets/images/chevron.left.png')}
                    />
                    <Text style={styles.headerTitle}>{titleMainNavigator}</Text>
                  </View>
                )}
              </Pressable>
            );
          },
        }}
        component={MainStack}
      />
    </Drawer.Navigator>
  );
};

export const CustomDrawerComp = function (props: DrawerContentComponentProps) {
  const dispatch = useDispatch();
  const {navigation} = props;

  return (
    <DrawerContentScrollView {...props}>
      <View style={{height: windowHeight, padding: 15}}>
        <View
          style={{
            flex: 1,
            flexDirection: 'row',
            justifyContent: 'center',
            marginBottom: windowHeight > 700 ? -120 : 0,
          }}>
          <Image
            style={{width: 113, resizeMode: 'contain'}}
            source={require('../../assets/images/netbird-logo-menu.png')}
          />
        </View>
        <View style={styles.drawerItem}>
          <DrawerItem
            label="Advanced"
            icon={({}) => (
              <Image
                style={styles.drawerIcon}
                source={require('../../assets/images/menu-advance4x.png')}
              />
            )}
            onPress={() => {
              dispatch(appStatusActions.setTitleMainNavigator('Advanced'));
              dispatch(appStatusActions.setShowLeftDrawer(true));
              navigation.navigate('Advanced');
            }}
            pressColor="#F68330"
            labelStyle={styles.label}
          />
        </View>
        <View style={styles.drawerItem}>
          <DrawerItem
            label="About"
            icon={({}) => (
              <Image
                style={styles.drawerIcon}
                source={require('../../assets/images/menu-about4x.png')}
              />
            )}
            onPress={() => {
              dispatch(appStatusActions.setTitleMainNavigator('About'));
              dispatch(appStatusActions.setShowLeftDrawer(true));
              navigation.navigate('About');
            }}
            pressColor="#F68330"
            labelStyle={styles.label}
          />
        </View>
        <View style={styles.drawerItem}>
          <DrawerItem
            label="Docs"
            icon={({}) => (
              <Image
                style={styles.drawerIcon}
                source={require('../../assets/images/menu-faq4x.png')}
              />
            )}
            onPress={() => {
              Linking.openURL('https://netbird.io/docs/').then(r => {
                return navigation.dispatch(DrawerActions.toggleDrawer());
              });
            }}
            pressColor="#F68330"
            labelStyle={styles.label}
          />
        </View>
        <View style={styles.drawerItem}>
          <DrawerItem
            label="Change Server"
            icon={({}) => (
              <Image
                style={styles.drawerIcon}
                source={require('../../assets/images/menu-change4x.png')}
              />
            )}
            onPress={() => {
              dispatch(appStatusActions.askChangeServer(true));
              navigation.dispatch(DrawerActions.closeDrawer());
            }}
            pressColor="#F68330"
            labelStyle={styles.label}
          />
        </View>
        <View
          style={{
            flexGrow: 1,
            alignSelf: 'center',
            justifyContent: 'flex-end',
          }}>
          <Text style={{alignItems: 'center', marginBottom: 25}}>
            <Text style={styles.items}>Version </Text>
            <Text style={[styles.items, {fontWeight: 'normal'}]}>
              {version}
            </Text>
          </Text>
        </View>
      </View>
    </DrawerContentScrollView>
  );
};

const styles = StyleSheet.create({
  titleContainer: {
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
  },
  headerTitle: {
    fontSize: 24,
    color: '#4D4D4D',
    fontWeight: '500',
    lineHeight: 32,
  },
  label: {
    fontSize: 16,
    color: '#4D4D4D',
    fontWeight: '500',
    lineHeight: 24,
  },
  items: {
    fontSize: 16,
    fontWeight: '500',
    color: colors.textDefault,
  },
  drawerItem: {marginVertical: windowHeight / 50},
  drawerIcon: {
    width: (windowHeight / windowWidth) * 15,
    height: (windowHeight / windowWidth) * 15,
  },
});
