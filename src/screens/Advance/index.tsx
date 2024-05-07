import React, {useEffect, useState} from 'react';
import {Text, View, StyleSheet, Dimensions} from 'react-native';
import Checkbox from 'expo-checkbox';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackScreenProps} from '@react-navigation/stack';
import Input from '@components/Input';
import {colors} from '../../theme';
import {Button} from '@components';
import {actions as appStatusActions} from '../../store/app-status';
import {useDispatch, useSelector} from 'react-redux';
import {useFocusEffect} from '@react-navigation/native';
import {RootState} from 'typesafe-actions';
import {
  isTraceLogEnabled,
  enableTraceLog,
  disableTraceLog,
  shareLog
} from 'react-native-netbird-lib';

type Props = StackScreenProps<RootStackParamList, 'Advance'>;

const windowWidth = Dimensions?.get('window')?.width;

const Advance: React.FC<Props> = ({navigation}) => {
  const dispatch = useDispatch();
  const advanceState = useSelector(
    (state: RootState) => state.appStatus.advance,
  );
  const [showInUse, setShowInUse] = useState(true);
  const [key, setKey] = useState('');
  const [traceLogUse, setTraceLogUse] = useState(false);
  const [updateTraceLog, setUpdateTraceLog] = useState(false);

  useEffect(() => {
    setShowInUse(advanceState?.inUse);
  }, [advanceState?.inUse]);

  useEffect(() => {
    if (updateTraceLog) {
      if (traceLogUse) {
        enableTraceLog();
      } else {
        disableTraceLog();
      }
      setUpdateTraceLog(false);
    }
  }, [traceLogUse]);

  useFocusEffect(
    React.useCallback(() => {
      isTraceLogEnabled().then((e:boolean) => {
        setTraceLogUse(e);
      });
      return () => {
        dispatch(appStatusActions.setTitleMainNavigator(''));
        dispatch(appStatusActions.setShowLeftDrawer(true));
      };
    }, []),
  );

  useEffect(() => {
    if (!advanceState?.saved) {
      return;
    }
    navigation.goBack();
  }, [advanceState?.saved]);

  const save = () => {
    if (advanceState?.saving) {
      return;
    }
    dispatch(
      appStatusActions.setAdvance({
        error: false,
        inUse: false,
        key: key,
        saving: true,
        saved: false,
        requested: true,
      }),
    );
  };

  const setTraceLog = (newValue:boolean) => {
    setTraceLogUse(newValue);
    setUpdateTraceLog(true);
  };

  return (
    <View style={styles.container}>
      {showInUse ? (
        <>
          <Text
            style={{
              fontSize: 16,
              fontWeight: 'bold',
              color: colors.textDefault,
            }}>
            Pre-shared key is in use
          </Text>
          <Text style={{fontSize: 16, color: colors.textDescription}}>
            Make sure your other peers have set the same key.
          </Text>
          <Text
            style={{
              paddingLeft: 15,
              fontSize: 16,
              color: colors.textDescription,
            }}>
            **********
          </Text>
          <Button
            text={'Change'}
            onPress={() => {
              setShowInUse(false);
            }}
          />
        </>
      ) : (
        <>
          <Text
            style={{
              fontSize: 16,
              fontWeight: 'bold',
              color: colors.textDefault,
            }}>
            Add a pre-shared key
          </Text>
          <Text style={{fontSize: 16, color: colors.textDescription}}>
            You will only communicate with peers that use the same key.
          </Text>
          <Input
            value={key}
            stylesMask={{borderRadius: 2}}
            onChangeText={text => setKey(text)}
            placeHolder={'Add a pre-shared key'}
            clearVisible={key.trim().length > 0}
            onClear={() => setKey('')}
          />
          <Button
            text={!advanceState.saving ? 'Save' : 'Saving...'}
            loading={advanceState.saving}
            onPress={() => save()}
          />
        </>
      )}
      <View style={{
          borderBottomColor: 'gray',
          borderBottomWidth: StyleSheet.hairlineWidth,
        }}
      />
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <Checkbox
          disabled={false}
          value={traceLogUse}
          onValueChange={(newValue) => setTraceLog(newValue)}
        />
        <Text style={{ marginLeft: 10,color: colors.textDescription }}>Enable trace log level.</Text>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <Button
            text={'Share logs'}
            onPress={() => shareLog()}
          />
      </View>
    </View>


  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.bgColor,
    gap: 16,
    paddingHorizontal: (windowWidth * 0.125),
    paddingVertical: 48
  },
});

export default Advance;
