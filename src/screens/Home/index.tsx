import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  AppState,
  Image,
  Text,
  TouchableOpacity,
  View,
  Dimensions,
  StyleSheet,
} from 'react-native';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackScreenProps} from '@react-navigation/stack';
import ModalAlert from '../../components/ModalAlert/index';
import {useDispatch, useSelector} from 'react-redux';
import {actions as appStatusActions} from '../../store/app-status';
import BottomPeers from '../../components/BottomPeers/index';
import {colors} from '../../theme';
import {RootState} from 'typesafe-actions';
import BottomSheet from '@gorhom/bottom-sheet';
import ListPeers from '@components/ListPeers';
import {
  ChangeServerStatusCode,
  switchConnection,
} from 'react-native-netbird-lib';
import SplashScreen from 'react-native-splash-screen';
import ButtonAnimationConnection, {
  ButtonAnimationConnectionRef,
} from '@components/ButtonAnimationConnection';
import {actions as appStatusPersistActions} from '../../store/app-status-persist';
import {openLink} from '../../components/InAppBrowser/utils';
import {InAppBrowser} from 'react-native-inappbrowser-reborn';

type Props = StackScreenProps<RootStackParamList, 'Home'>;

const windowWidth = Dimensions?.get('window').width;
const windowHeight = Dimensions?.get('window').height;

const Home: React.FC<Props> = ({navigation}) => {
  const dispatch = useDispatch();

  const onboardingToChangeServerState = useSelector(
    (state: RootState) => state.appStatus.onboardingToChangeServer,
  );
  const connectionState = useSelector(
    (state: RootState) => state.appStatus.connection,
  );

  const onOpenLink = useCallback(async (linkUrl: string) => {
    await openLink(linkUrl);
  }, []);

  const openedBrowserRef = useRef(false);

  useEffect(() => {
    if (connectionState?.webView && !changeServerState?.status) {
      onOpenLink(connectionState?.webView);
      openedBrowserRef.current = true;
    }
  }, [connectionState?.webView]);

  const peersTotalState = useSelector(
    (state: RootState) => state.appStatus.peers.total,
  );
  const peersTotalConnectedState = useSelector(
    (state: RootState) => state.appStatus.peers.connected,
  );

  const advanceState = useSelector(
    (state: RootState) => state.appStatus.advance,
  );

  const resetState = useSelector((state: RootState) => state.appStatus.reset);

  const changeServerState = useSelector(
    (state: RootState) => state.appStatus.changeServer,
  );

  const dnsState = useSelector(
    (state: RootState) => state.appStatusPersist.writeDNS,
  );
  const ip = useSelector((state: RootState) => state.appStatusPersist.writeIP);

  const buttonAnimationConnRef = useRef<ButtonAnimationConnectionRef>(null);
  const bottomSheetRef = useRef<BottomSheet>(null);
  const [bottomSheetOpened, setBottomSheetOpened] = useState<boolean>(true);
  const handleSheetChanges = useCallback((index: number) => {
    console.log('handleSheetChanges', index);
  }, []);

  const closeInAppBrowser = async () => {
    try {
      await InAppBrowser.close();
      console.log('InAppBrowser closed successfully');
    } catch (error) {
      console.error('Failed to close InAppBrowser:', error);
    }
  };

  const connectedRef = useRef(connectionState.connected);
  useEffect(() => {
    connectedRef.current = connectionState.connected;
    if (connectionState?.connected) {
      closeInAppBrowser();
      dispatch(appStatusActions.setWebViewOpen({webView: null}));
    }
  }, [connectionState?.connected]);

  useEffect(() => {
    if(connectionState.error?.message !== undefined){
    closeInAppBrowser();
    dispatch(appStatusActions.requestConnection(false));
    dispatch(appStatusActions.setConnecting(false));
    dispatch(appStatusActions.setWebViewOpen({webView: null}));
    openedBrowserRef.current = false;
    connectedRef.current = connectionState.connected;
    }
  }, [connectionState.error?.message]);

  AppState.addEventListener('focus', () => {
    if (
      openedBrowserRef.current &&
      connectionState?.webView &&
      !changeServerState?.status &&
      !connectedRef.current
    ) {
      setTimeout(() => {
        if (!connectedRef.current) {
          console.log(
            new Date().toLocaleString(),
            'closing connection after timeout',
          );
          switchConnection(false);
        }
      }, 25000);
      openedBrowserRef.current = false;
      dispatch(appStatusActions.setWebViewOpen({webView: null}));
    }
  });

  useEffect(() => {
    dispatch(appStatusActions.setTitleMainNavigator(''));
    dispatch(appStatusActions.setShowLeftDrawer(true));
    SplashScreen.hide();

    if (onboardingToChangeServerState) {
      onChangeServer();
      dispatch(appStatusActions.setOnboardingToChangeServer(false));
    }
  }, []);

  useEffect(() => {
    if (changeServerState.status) {
      dispatch(appStatusPersistActions.writeDNS(''));
      dispatch(appStatusPersistActions.writeIP(''));
    }
  }, [changeServerState]);

  const onChangeServer = () => {
    dispatch(appStatusActions.resetChangeServer());
    dispatch(appStatusActions.setTitleMainNavigator('Change Server'));
    dispatch(appStatusActions.setShowLeftDrawer(true));

    if (connectionState.connecting) {
      dispatch(appStatusActions.requestCancelConnection(true));
    }

    if (connectionState.connected) {
      buttonAnimationConnRef.current?.toggleConnection();
    }
    navigation.navigate('ChangeServer');
  };

  const openBottomSheet = () => {
    setBottomSheetOpened(true);
    bottomSheetRef?.current?.snapToIndex(0);
  };
  const closeBottomSheet = () => bottomSheetRef?.current?.close();

  return (
    <View style={{flex: 1}}>
      <View style={{flex: 0, alignItems: 'center', gap: 8, marginBottom: 60}}>
        <Text style={styles.TextServer}>{dnsState}</Text>
        <Text style={styles.TextServer}>{ip}</Text>
      </View>
      <View
        style={{
          flex: 1,
          width: '100%',
          alignItems: 'center',
          position: 'relative',
          paddingTop: 8,
        }}>
        <View
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            justifyContent: 'flex-start',
            alignItems: 'center',
          }}>
          <View
            style={{
              backgroundColor: colors.bgColor,
              position: 'absolute',
              left: 0,
              right: 0,
              bottom: 0,
              top: windowHeight / 3,
            }}
          />
          <Image
            style={{
              width: windowWidth,
              height: windowWidth * 1.33,
              resizeMode: 'stretch',
            }}
            source={require('../../assets/images/bg-bottom.png')}
          />
        </View>

        <View
          style={{
            flex: 0,
            width: windowWidth,
            alignItems: 'center',
            paddingTop: windowHeight / (windowWidth < 300 ? 27 : 21),
          }}>
          <ButtonAnimationConnection ref={buttonAnimationConnRef} />
        </View>

        {
          <View
            style={{
              flex: 1,
              alignItems: 'center',
              justifyContent: 'flex-end',
              width: '102%',
            }}>
            <TouchableOpacity
              style={{width: '100%'}}
              onPress={() => {
                openBottomSheet();
              }}>
              <BottomPeers
                textCount={peersTotalConnectedState}
                textTotal={peersTotalState}
              />
            </TouchableOpacity>
          </View>
        }
      </View>
      <ModalAlert
        visible={resetState.askReset}
        autoClose={false}
        body={() => (
          <View style={{flex: 1}}>
            <View
              style={{
                flexDirection: 'row',
                gap: 5,
                alignItems: 'center',
                width: 15,
                resizeMode: 'contain',
              }}>
              <Image
                source={require('../../assets/images/exclamation-circle.png')}
              />
              <Text
                style={{
                  fontSize: 18,
                  fontWeight: 'bold',
                  color: colors.textDefault,
                }}>
                Are you want to reset?
              </Text>
            </View>
            <Text
              style={{
                fontSize: 14,
                color: colors.textDefault,
                marginTop: 15,
                marginLeft: 28,
              }}>
              {
                'All server information will be \nremoved, you will be redirected to \nlogin screen'
              }
            </Text>
          </View>
        )}
        textOkButton="Yes"
        onOk={() => {
          dispatch(appStatusActions.confirmReset(true));
        }}
        textCancelButton="No"
        onCancel={() => {
          dispatch(appStatusActions.askReset(false));
        }}
        onPressOverlay={() => {
          dispatch(appStatusActions.askReset(false));
        }}
      />

      <ModalAlert
        visible={advanceState.saved}
        iconSource={require('@assets/images/check-circle.png')}
        title={'Pre-shared Key'}
        text={"The peer's pre-shared key was changed."}
        textOkButton="ok"
        autoClose={true}
        onOk={() => {
          dispatch(
            appStatusActions.setAdvance({
              ...advanceState,
              saved: false,
              key: '',
            }),
          );
        }}
        textCancelButton="No"
        onPressOverlay={() => {
          dispatch(
            appStatusActions.setAdvance({
              ...advanceState,
              saved: false,
              key: '',
            }),
          );
        }}
      />

      <ModalAlert
        visible={changeServerState.ask}
        iconSource={require('@assets/images/exclamation-circle.png')}
        title={'Change server'}
        text={
          'Changing server will erase the local config and disconnect this device from the current NetBird account.'
        }
        autoClose={false}
        textOkButton="Yes"
        onOk={() => onChangeServer()}
        textCancelButton="Cancel"
        onCancel={() => {
          dispatch(appStatusActions.askChangeServer(false));
        }}
        onPressOverlay={() => {
          dispatch(appStatusActions.askChangeServer(false));
        }}
      />

      <ModalAlert
        visible={
          changeServerState.status === ChangeServerStatusCode.CHANGED ||
          changeServerState.status === ChangeServerStatusCode.SSO_IS_SUPPORTED
        }
        autoClose={true}
        iconSource={require('@assets/images/check-circle.png')}
        title={'Server was changed'}
        text={'Click on the connect button to continue.'}
        textOkButton="Ok"
        onOk={() => {
          dispatch(appStatusActions.resetChangeServer());
        }}
        onPressOverlay={() => {
          dispatch(appStatusActions.resetChangeServer());
        }}
      />
      <ModalAlert
        visible={!!connectionState.error?.message}
        autoClose={false}
        iconSource={require('@assets/images/exclamation-circle.png')}
        title={'Something went wrong'}
        text={connectionState.error?.message}
        textOkButton="Ok"
        onOk={() => {
          dispatch(appStatusActions.setWebViewError({error: null}));
          dispatch(appStatusActions.setConnectionStatus(false));
        }}
        onPressOverlay={() => {
          dispatch(appStatusActions.setWebViewError({error: null}));
          dispatch(appStatusActions.setConnectionStatus(false));
        }}
      />

      <BottomSheet
        ref={bottomSheetRef}
        index={-1}
        backgroundStyle={{backgroundColor: colors.bgColor, borderRadius: 30}}
        handleIndicatorStyle={{
          backgroundColor: 'rgba(217, 217, 217, 1)',
          width: 115,
          marginTop: 16,
          marginBottom: -20,
          height: 6,
          borderRadius: 3,
        }}
        onClose={() => setBottomSheetOpened(false)}
        enablePanDownToClose={true}
        enableContentPanningGesture={true}
        snapPoints={['100%']}
        onChange={handleSheetChanges}>
        {bottomSheetOpened && (
          <ListPeers bottomSheet={true} onClose={closeBottomSheet} />
        )}
      </BottomSheet>
    </View>
  );
};

const styles = StyleSheet.create({
  TextServer: {
    color: 'rgba(0, 0, 0, 0.4)',
    fontSize: 16,
    fontWeight: 'normal',
    lineHeight: 24,
  },
});

export default Home;
