import React, {useEffect, useState} from 'react';
import {
  FlatList,
  Platform,
  StyleSheet,
  Text,
  TouchableHighlight,
  View,
} from 'react-native';
import {RootStackParamList} from '@navigation/RootStackParamList';
import {StackScreenProps} from '@react-navigation/stack';
import styled from 'styled-components/native';
import {colors} from '../../theme';
import {useFocusEffect} from '@react-navigation/native';
import {actions as appStatusActions} from '../../store/app-status';
import {useDispatch, useSelector} from 'react-redux';
import {RootState} from 'typesafe-actions';
import {Peer} from '../../store/app-status/types';
import ListPeers from "@components/ListPeers";

type Props = StackScreenProps<RootStackParamList, 'Home'>;

const Peers: React.FC<Props> = ({navigation}) => {
  const dispatch = useDispatch();

  useFocusEffect(
    React.useCallback(() => {
      return () => {
        dispatch(appStatusActions.setTitleMainNavigator(''));
        dispatch(appStatusActions.setShowLeftDrawer(true));
      };
    }, []),
  );

  return (
    <ListPeers/>
  )
};

export default Peers;
