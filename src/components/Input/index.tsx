import React from 'react';
import {
  StyleSheet,
  Text,
  Image,
  ViewStyle,
  View,
  TextInput,
  TouchableOpacity,
} from 'react-native';
import {NativeSyntheticEvent} from 'react-native/Libraries/Types/CoreEventTypes';
import {TextInputChangeEventData} from 'react-native/Libraries/Components/TextInput/TextInput';
import {colors} from '../../theme';

interface Props {
  onChange?: (e: NativeSyntheticEvent<TextInputChangeEventData>) => void;
  onChangeText?: (text: string) => void;
  onTextInput?: () => void;
  styles?: StyleSheet.NamedStyles<any>;
  stylesMask?: any;
  editable?: boolean;
  value?: string;
  placeHolder?: string;
  keyboardType?: string;
  error?: boolean;
  errorMessage?: string;
  clearVisible?: boolean;
  onClear?: () => void;
}

const stylesPallet = {
  NbMaskTextInput: {
    fontSize: 14,
    lineHeight: 22,
    height: 40,
    borderWidth: 1,
    borderColor: 'rgba(217, 217, 217, 1)',
    paddingHorizontal: 0,
    paddingVertical: 2,
    backgroundColor: 'white',
    flexDirection: 'row',
    alignItems: 'center',
    overFlow: 'hidden',
  } as ViewStyle,
  NbTextInput: {
    color: 'rgba(77, 77, 77, 1)',
    flex: 1,
    height: 40,
    paddingHorizontal: 8,
    paddingVertical: 2,
    fontFamily: 'Roboto',
    fontSize: 14,
    lineHeight: 22,
    fontWeight: '500',
  } as ViewStyle,

  error: {
    borderColor: colors.error,
    padding: 3,
    position: 'relative', 
    color: '#A8071A'
  } as ViewStyle,
};

const Input: React.FC<Props> = ({
  onChange,
  onChangeText,
  onTextInput,
  styles,
  stylesMask,
  editable,
  value,
  placeHolder,
  error,
  errorMessage,
  clearVisible,
  onClear,
}) => {

  return (
    <>
      <View style={[stylesPallet.NbMaskTextInput, stylesMask]}>
        <TextInput
          onChange={onChange}
          onChangeText={onChangeText}
          onTextInput={onTextInput}
          style={[
            styles,
            stylesPallet.NbTextInput
          ]}
          placeholderTextColor={'rgba(0,0,0,0.25)'}
          editable={editable}
          value={value}
          placeholder={placeHolder}
        />
        {clearVisible && (
          <TouchableOpacity
            style={{padding: 10}}
            onPress={() => onClear && onClear()}>
            <Image
              source={require('@assets/images/close-slider-4x.png')}
              style={{width: 15, height: 15, resizeMode: 'contain'}}
            />
          </TouchableOpacity>
        )}
      </View>
      {error && (
        <Text style={stylesPallet.error}>
          {errorMessage}
        </Text>
      )}
    </>
  );
};

export default React.memo(Input);
