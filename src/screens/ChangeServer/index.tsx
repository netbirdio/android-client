import React, {useEffect, useState} from 'react';
import {Image, Text, ScrollView, StyleSheet, View, Dimensions} from 'react-native';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackScreenProps} from '@react-navigation/stack';
import styled from 'styled-components/native';
import {useDispatch, useSelector} from 'react-redux';
import {useFocusEffect} from '@react-navigation/native';
import {actions as appStatusActions} from '../../store/app-status';
import {colors, form} from '../../theme';
import Input from '@components/Input';
import {Button} from '@components';
import {RootState} from 'typesafe-actions';
import {ChangeServerData} from '../../store/app-status/types';
import {ChangeServerStatusCode} from 'react-native-netbird-lib';

const windowHeight = Dimensions?.get('window').height;
const windowWidth = Dimensions?.get('window').width;

type Props = StackScreenProps<RootStackParamList, 'Home'>;

const ChangeServer: React.FC<Props> = ({navigation}) => {
  const dispatch = useDispatch();

  const changeServerState = useSelector(
    (state: RootState) => state.appStatus.changeServer,
  );
  const [error, setError] = useState<boolean>(false);
  const [dataServer, setDataServer] = useState<ChangeServerData>({
    server: '',
    setupKey: '',
  });

  useFocusEffect(
    React.useCallback(() => {
      return () => {
        dispatch(appStatusActions.setTitleMainNavigator(''));
        dispatch(appStatusActions.setShowLeftDrawer(true));
      };
    }, []),
  );

  useEffect(() => {
    setDataServer({...changeServerState.data});
  }, []);

  useEffect(() => {
    if (
      changeServerState.status === ChangeServerStatusCode.CHANGED ||
      changeServerState.status === ChangeServerStatusCode.SSO_IS_SUPPORTED
    ) {
      navigation.goBack();
    }
  }, [changeServerState.status]);

  const validateUrl = url => {
    // Regular expression to match a valid URL address
    const urlRegex =
      /^(https?:\/\/)?([\da-z.-]+)\.([a-z.]{2,})(:\d+)?([\/\w.-]*)*$/;
    const lowerCaseURL = url.toLowerCase();
    // Check if the URL matches the regex pattern
    return urlRegex.test(lowerCaseURL);
  };

  const onChangeServer = (server: string) => {
    if (validateUrl(server)) {
      setDataServer({...dataServer, server});
      setError(false);
    } else {
      setDataServer({...dataServer, server});
      setError(true);
    }
  };

  const onChangeSetupKey = (setupKey: string) =>
    setDataServer({...dataServer, setupKey});

  const checkServer = () => {
    dispatch(
      appStatusActions.requestCheckServer({status: true, data: dataServer}),
    );
  };

  const changeServerSetupKey = () => {
    dispatch(
      appStatusActions.requestChangeServerSetupKey({
        status: true,
        data: dataServer,
      }),
    );
  };

  const onPressChangeServer = () => {
    console.log('CLICK CHECK');
    if (changeServerState.checking || changeServerState.changing) {
      return;
    }

    if (
      changeServerState.status === ChangeServerStatusCode.NONE ||
      changeServerState.status === ChangeServerStatusCode.CHECK_ERROR
    ) {
      checkServer();
    }

    if (
      changeServerState.status === ChangeServerStatusCode.SSO_IS_NOT_SUPPORTED
    ) {
      changeServerSetupKey();
    }
  };

  const onPressUserNetbirdServer = () => {
    if (changeServerState.checking || changeServerState.changing) {
      return;
    }
    dispatch(
      appStatusActions.setChangeServerStatus(ChangeServerStatusCode.NONE),
    );
    dispatch(
      appStatusActions.requestCheckServer({
        status: true,
        data: {server: '', setupKey: ''},
      }),
    );
    setDataServer({server: '', setupKey: ''});
  };

  return (
    <View style={{flex: 1, backgroundColor: colors.bgColor}}>
      <ScrollView>
        <View style={styles.NbFormArea}>
          <View style={styles.NbFormContent}>
            <View style={styles.NbFormInputGroup}>
              <Text style={styles.NbFormTextLabel}>Server</Text>
              <Input
                stylesMask={{borderRadius: 2}}
                placeHolder={'https://example-api.domain.com:443'}
                value={dataServer.server}
                onChangeText={e => onChangeServer(e)}
                error={
                  changeServerState.status ===
                    ChangeServerStatusCode.CHECK_ERROR || error
                }
                errorMessage={'Invalid server address'}
                clearVisible={dataServer.server.trim().length > 0}
                onClear={() => onChangeServer('')}
              />
            </View>
            {(changeServerState.status ===
              ChangeServerStatusCode.SSO_IS_NOT_SUPPORTED ||
              changeServerState.status ===
                ChangeServerStatusCode.CHANGED_ERROR) && (
              <View style={styles.NbFormInputGroup}>
                <Text style={styles.NbFormTextLabel}>Setup key</Text>
                <Input
                  stylesMask={{borderRadius: 2}}
                  placeHolder={'Key'}
                  value={dataServer.setupKey}
                  onChangeText={e => onChangeSetupKey(e)}
                  error={
                    changeServerState.status ===
                    ChangeServerStatusCode.CHANGED_ERROR
                  }
                  errorMessage={'Error setup key address'}
                  clearVisible={dataServer.setupKey.trim().length > 0}
                  onClear={() => onChangeSetupKey('')}
                />
              </View>
            )}
          </View>
          <View style={styles.NbButtonGroup}>
            <Button
              text={
                changeServerState.checking || changeServerState.changing
                  ? 'Verifing...'
                  : 'Change'
              }
              loading={changeServerState.checking || changeServerState.changing}
              onPress={() => onPressChangeServer()}
            />
            {!changeServerState.checking && !changeServerState.changing && (
              <Button
                text={'Use NetBird server'}
                outline={true}
                onPress={() => onPressUserNetbirdServer()}
                imagePrefix={() => (
                  <Image
                    source={require('@assets/images/icon-netbird-button.png')}
                    style={{width: 14, height: 14, marginLeft: 8}}
                  />
                )}
              />
            )}
          </View>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  NbFormArea: {
    flex: 1,
    paddingHorizontal: 24,
    paddingVertical: 48,
  },
  NbFormContent: {
    marginBottom: 15,
  },
  NbFormTextLabel: {
    marginBottom: 5,
    fontWeight: '400',
    color: 'rgba(77, 77, 77, 1)',
    fontSize: (windowWidth / (windowHeight > 630 ? 19 : 21))
  },
  NbFormInputGroup: {
    marginBottom: 10,
  },
  NbButtonGroup: {
    flex: 1,
    gap: 25
  },
});

export default ChangeServer;
