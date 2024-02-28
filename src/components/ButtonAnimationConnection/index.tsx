import React, {useEffect, useImperativeHandle, useRef, useState} from 'react';
import {
  Text,
  Dimensions,
  Animated,
  TouchableWithoutFeedback,
  View,
  Image,
} from 'react-native';
import LottieView from 'lottie-react-native';
import DeviceInfo from 'react-native-device-info';
import {useDispatch, useSelector} from 'react-redux';
import {actions as appStatusActions} from '../../store/app-status';
import {RootState} from 'typesafe-actions';
import {StyleSheet} from 'react-native';
import AsyncStorage from '@react-native-community/async-storage';
import ModalAlert from '@components/ModalAlert';

const windowWidth = Dimensions?.get('window')?.width;
const windowHeight = Dimensions?.get('window')?.height;
const width = Dimensions?.get('window')?.width ?? 0;

type Props = {};

export type ButtonAnimationConnectionRef = {
  toggleConnection: () => void;
};

const ButtonAnimationConnection = React.forwardRef<
  ButtonAnimationConnectionRef,
  Props
>((props, ref) => {
  const dispatch = useDispatch();

  const connectionState = useSelector(
    (state: RootState) => state.appStatus?.connection,
  );
  const [showAlert, setShowAlert] = useState<boolean>(false);
  const [textStatus, setTextStatus] = useState<string>('Disconnected');
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [loLooping, setLoLooping] = useState<boolean>(false);

  const animationRef = useRef<LottieView>(null);
  const animationValue = useRef(new Animated.Value(1)).current;

  useImperativeHandle(ref, () => ({
    toggleConnection: toggleConnection as () => void, // Assert toggleConnection to the correct type
  }));

  useEffect(() => {
    if (connectionState?.connecting && !isPlaying) {
      loConnecting();
    }
  }, [connectionState, isPlaying]);

  useEffect(() => {
    const checkFirstLaunch = async () => {
      const isFirstLaunch = await AsyncStorage.getItem('isFirstLaunch');
      if (isFirstLaunch === null) {
        // App is being launched for the first time after being killed
        await AsyncStorage.setItem('isFirstLaunch', 'false');
      } else {
        // App has been launched before after being killed
        if (connectionState.connected) {
          loConnecting();
          setTextStatus('Connecting...');
        }
      }
    };
    checkFirstLaunch();
  }, []);

  useEffect(() => {
    if (connectionState?.disconnecting) {
      setTextStatus('Disconnecting...');
      loDisconnecting();
      dispatch(appStatusActions.setWebViewError({error: null}));
    }
  }, [connectionState?.disconnecting]);

  useEffect(() => {
    if (connectionState?.requestedCancel) {
      loConnecting();
    }
  }, [connectionState?.requestedCancel]);

  useEffect(() => {
    if (!connectionState?.requested) {
      return;
    }

    if (!connectionState?.connected) {
      loRequestConnect();
    }
  }, [connectionState?.requested]);

  useEffect(() => {
    if (connectionState?.disconnecting) {
      return setTextStatus('Disconnecting...');
    } else if (connectionState?.connecting) {
      if (connectionState?.timeout) {
        return setTextStatus('Connecting... click again to cancel');
      } else {
        return setTextStatus('Connecting...');
      }
    }
    if (connectionState.error?.notification) {
      setShowAlert(true);
    }
  }, [connectionState]);

  const loConnecting = () => {
    animationRef.current?.play(78, 120);
    setIsPlaying(true);
  };

  const loRequestConnect = () => {
    animationRef.current?.play(0, 78);
  };

  const loDisconnecting = () => {
    if (!connectionState.timeout) {
      animationRef.current?.play(152, 309);
      return;
    }
    loConnecting();
  };

  const loConnected = () => {
    animationRef.current?.play(121, 150);
    setIsPlaying(true);
  };
  const loConnectedEnd = () => {
    animationRef.current?.play(150, 150);
  };
  const loDisconnected = () => {
    animationRef.current?.play(311, 339);
    setIsPlaying(true);
  };

  const loDisconnectedEnd = () => {
    animationRef.current?.play(339, 339);
  };

  const handlePress = (isPressed: boolean) => {
    Animated.spring(animationValue, {
      toValue: isPressed ? 0.95 : 1,
      useNativeDriver: true,
    }).start();
  };

  const toggleConnection = () => {
    if (connectionState?.timeout) {
      dispatch(appStatusActions.requestCancelConnection(true));
      return;
    }
    if (connectionState?.requested) {
      return;
    }
    dispatch(appStatusActions.requestConnection(true));
  };

  const onAnimationFinish = () => {
    setIsPlaying(true);
    if (connectionState?.disconnecting) {
      setTextStatus('Disconnecting...');
      loDisconnecting();
      setLoLooping(true);
      return;
    } else if (connectionState?.connecting) {
      if (connectionState?.timeout) {
        setTextStatus('Connecting... click again to cancel');
      } else {
        setTextStatus('Connecting...');
      }
      loConnecting();
      setLoLooping(true);
      return;
    } else if (connectionState?.connected && loLooping) {
      loConnected();
      setLoLooping(false);
    } else if (connectionState?.connected && !loLooping) {
      loConnectedEnd();
      setTextStatus('Connected');
    } else if (!connectionState?.connected && loLooping) {
      loDisconnected();
    } else if (!connectionState?.connected && !loLooping) {
      loDisconnectedEnd();
      setTextStatus('Disconnected');
    }
    setLoLooping(false);
  };

  const renderAlertIcon = () => {
    if (connectionState.error?.notificationInApp) {
      return (
        <Image
          style={{
            marginTop: width / (windowHeight > 630 ? 90 : -20),
            marginLeft: 7,
            height: 22,
            width: 22,
            resizeMode: 'contain',
          }}
          source={require('../../assets/images/exclamation-circleY.png')}
        />
      );
    }
    return null;
  };

  return (
    <>
      <TouchableWithoutFeedback
        style={styles.container}
        onPress={toggleConnection}
        id="buttonAnimationId"
        hitSlop={{top: -30, bottom: -30, left: -30, right: -30}}
        onPressIn={() => handlePress(true)}
        onPressOut={() => handlePress(false)}>
        <Animated.View
          style={[styles.button, {transform: [{scale: animationValue}]}]}>
          <LottieView
            ref={animationRef}
            style={{
              transform: [
                {
                  // Scale the Tablet component If the window width is greater or less than 799
                  scale:
                    DeviceInfo.getDeviceType() === 'Tablet'
                      ? windowWidth > 799
                        ? 1.55
                        : 1.3
                      : // Scale the Phone component If the window width is between 400 and 799
                      windowWidth >= 400 && windowWidth <= 799
                      ? (windowWidth + 50) / windowWidth
                      : // Scale the Phone component If the window width is less than 400
                        (windowWidth + 10) / windowWidth,
                },
              ],
            }}
            source={require('../../assets/animations/button-full2.json')}
            autoPlay={false}
            hardwareAccelerationAndroid={true}
            loop={false}
            onAnimationFinish={onAnimationFinish}
          />
        </Animated.View>
      </TouchableWithoutFeedback>

      <ModalAlert
        visible={showAlert}
        autoClose={false}
        iconSource={require('@assets/images/exclamation-circleY.png')}
        text={
          'Your network administrator changed a configuration that requires restarting the connection.'
        }
        textOkButton="Ok"
        onOk={() => {
          setShowAlert(false);
          dispatch(appStatusActions.setWebViewError({error: null}));
        }}
        onPressOverlay={() => {
          setShowAlert(false);
          dispatch(appStatusActions.setWebViewError({error: null}));
        }}
      />

      <TouchableWithoutFeedback
        onPress={() => {
          connectionState.error?.notificationInApp && setShowAlert(true);
        }}
        id="alertButtonAnimationId">
        <View style={{flexDirection: 'row'}}>
          <Text style={styles.TextStatus}>{textStatus}</Text>
          {renderAlertIcon()}
        </View>
      </TouchableWithoutFeedback>
    </>
  );
});

export default ButtonAnimationConnection;

const styles = StyleSheet.create({
  container: {
    marginTop: windowHeight > 630 ? -25 : -5,
    borderRadius: 200,
    display: 'flex',
    justifyContent: 'center',
    alignContent: 'center',
  },
  TextStatus: {
    marginTop: width / (windowHeight > 630 ? 90 : -20),
    fontSize: width / (windowHeight > 630 ? 19 : 20),
    fontWeight: '500',
    color: 'rgba(0, 0, 0, 0.35)',
  },
  button: {
    // Check the device type using DeviceInfo.getDeviceType()
    // If it's a 'Tablet', then determine the marginTop based on windowHeight is 1280 and greater set marginTop to -90, otherwise set it to -65
    // If it's a 'Phone', determine the marginTop based on windowHeight is greater than 610, set marginTop to -45, otherwise set it to -30
    marginTop:
      DeviceInfo.getDeviceType() === 'Tablet'
        ? windowHeight > 1279
          ? -90 : -65
        : windowHeight > 610
        ? -45 : -30,
    width: windowWidth - 50,
    height: windowWidth - 50,
  },
});
