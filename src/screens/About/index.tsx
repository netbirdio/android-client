import React from 'react';
import {Button, Image, Linking, StyleSheet, Text, View} from 'react-native';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackScreenProps} from '@react-navigation/stack';
import {colors} from '../../theme';
import {useFocusEffect} from '@react-navigation/native';
import {actions as appStatusActions} from '../../store/app-status';
import {useDispatch} from 'react-redux';
import DeviceInfo from 'react-native-device-info';

type Props = StackScreenProps<RootStackParamList, 'Home'>;

const About: React.FC<Props> = ({navigation}) => {
  const dispatch = useDispatch();
  var version = DeviceInfo.getVersion();

  useFocusEffect(
    React.useCallback(() => {
      return () => {
        dispatch(appStatusActions.setTitleMainNavigator(''));
        dispatch(appStatusActions.setShowLeftDrawer(true));
      };
    }, []),
  );

  return (
    <View style={styles.container}>
      <View style={styles.container}>
        <Image
          source={require('../../assets/images/netbird-logo-menu.png')}
          style={{marginTop: 75, marginBottom: 41}}
        />
        <View style={{flex: 1, alignItems: 'center', gap: 32}}>
          <Text>
            <Text style={styles.items}>Version </Text>
            <Text style={[styles.items, {fontWeight: 'normal'}]}>
              {version}
            </Text>
          </Text>
          <Text
            id="licenseLink"
            style={[styles.items, styles.link]}
            onPress={() => Linking.openURL('https://netbird.io/terms')}>
            License agreement
          </Text>
          <Text
            style={[styles.items, styles.link]}
            onPress={() => Linking.openURL('https://netbird.io/privacy')}>
            Privacy policy
          </Text>
        </View>

        <Text style={{marginBottom: 50,color: colors.textDefault,}}>
          © {new Date().getFullYear()} NetBird all rights reserved
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  items: {
    fontSize: 16,
    fontWeight: 'bold',
    color: colors.textDefault,
  },
  link: {
    fontSize: 16,
    lineHeight: 24,
    color: colors.default,
    textDecorationLine: 'underline',
  },
  container: {
    flex: 1,
    alignItems: 'center',
    gap: 50,
  },
});

export default About;
