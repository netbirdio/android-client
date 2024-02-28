import React, {useEffect} from 'react';
import {useDispatch} from 'react-redux';
import {Image, StyleSheet, Text, View} from 'react-native';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackScreenProps} from '@react-navigation/stack';
import Button from '../../components/Button/index';
import {actions as appStatusActions} from '../../store/app-status';
import SplashScreen from 'react-native-splash-screen';
import {actions as appStatusPersistActions} from '../../store/app-status-persist';

type Props = StackScreenProps<RootStackParamList, 'Onboarding'>;

const Onboarding: React.FC<Props> = ({navigation}) => {
  const dispatch = useDispatch();
  useEffect(() => SplashScreen.hide(), []);

  const onContinue = (showChangeServer: boolean) => {
    dispatch(appStatusActions.setShowLeftDrawer(true));
    dispatch(appStatusActions.setTitleMainNavigator(''));
    dispatch(appStatusPersistActions.setOnboardingShowed(true));
    dispatch(appStatusActions.setOnboardingToChangeServer(showChangeServer));
    navigation.navigate('Home');
  };

  return (
    <View
      style={{
        flex: 1,
        justifyContent: 'center',
        backgroundColor: '#F2F2F2',
      }}>
      <View style={styles.Wrapper}>
        <View style={{alignItems: 'center'}}>
          <Image
            style={styles.HeroImage}
            source={require('@assets/images/logo-onboarding.png')}
          />
        </View>
        <Text style={styles.Title}>
          <Text>
            By default you will connect to {'\n'} NetBird's cloud servers. You
            can {'\n'} access the{' '}
          </Text>
          <Text
            testID="123"
            style={styles.TitleLink}
            onPress={() => onContinue(true)}>
            change server
          </Text>
          <Text> menu to use {'\n'} another server.</Text>
        </Text>
        <Button onPress={() => onContinue(false)} text="Continue" />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  HeroImage: {
    width: 118,
    marginBottom: 88,
    resizeMode: 'contain',
  },
  Wrapper: {
    paddingHorizontal: 41,
    paddingVertical: 41,
  },
  container: {
    paddingHorizontal: 0,
    paddingVertical: 41,
    alignItems: 'center',
  },
  Title: {
    textAlign: 'center',
    marginBottom: 38,
    paddingHorizontal: 0,
    paddingVertical: 2,
    lineHeight: 22,
    color: '#000000',
    fontSize: 16,
    fontWeight: '500',
    fontFamily: 'Roboto',
  },
  TitleLink: {
    color: '#F68330',
    fontSize: 16,
    fontWeight: '500',
    fontFamily: 'Roboto',
    textDecorationLine: 'underline',
  },
});

export default Onboarding;
