import React, { useEffect } from 'react';
import { Image, Modal, Text, TouchableWithoutFeedback, View, ViewStyle } from 'react-native';
import Button from '../Button/index';

interface Props {
  title?: string;
  text?: string;
  visible: boolean;
  autoClose?: boolean;
  textOkButton?: string;
  onOk?: () => void;
  textCancelButton?: string;
  onCancel?: () => void;
  iconSource?: any;
  body?: () => JSX.Element;
  messageContent?: () => JSX.Element;
  onPressOverlay?: () => void | undefined;
  onPress?: () => void;
}

const styles = {
  NbModalCard: {
    padding: 24,
    backgroundColor: '#FFF',
    marginLeft: 22,
    marginRight: 22,
    borderRadius: 14,
  } as ViewStyle,
  NbModalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    justifyContent: 'center',
  } as ViewStyle,
  NbModalContent: {
    flexDirection: 'column',
    alignItems: 'center',
    marginBottom: 24,
    paddingTop: 17,
  } as ViewStyle,
  NbModalBody: {
    paddingHorizontal: 0,
    paddingVertical: 15,
  } as ViewStyle,
  NbModalTextTitle: {
    fontSize: 18,
    fontWeight: '700',
    lineHeight: 24,
    marginBottom: 8,
    color: 'rgba(0, 0, 0, 0.85)',
    textAlign: 'center',
  } as ViewStyle,
  NbModalBottomOptions: {
    flexDirection: 'column',
    gap: 15,
  } as ViewStyle,
  NbModalTextContent: {
    fontSize: 16,
    lineHeight: 22,
    color: 'rgba(77, 77, 77, 1)',
    textAlign: 'center',
  } as ViewStyle,
};

const ModalAlert: React.FC<Props> = ({
  title,
  text,
  visible,
  autoClose,
  textOkButton,
  onOk,
  textCancelButton,
  onCancel,
  iconSource,
  body,
  messageContent,
  onPressOverlay,
}) => {
  useEffect(() => {
    if (visible && autoClose) {
      setTimeout(() => {
        onPressOverlay && onPressOverlay();
      }, 5000);
    }
  }, [visible]);

  return (
    <Modal visible={visible} transparent={true} animationType='fade'>
      <TouchableWithoutFeedback id='overlayId' onPress={onPressOverlay}>
        <View style={styles.NbModalBackdrop}>
          <View style={styles.NbModalCard}>
            <TouchableWithoutFeedback>
              <View style={styles.NbModalContent}>
                {iconSource && (
                  <Image
                    source={iconSource}
                    style={{
                      marginRight: 5,
                      marginBottom: 15,
                      resizeMode: 'contain',
                    }}
                  />
                )}
                {!body ? (
                  <View style={styles.NbModalBody}>
                    <Text style={styles.NbModalTextTitle}>{title}</Text>
                    {text && <Text style={styles.NbModalTextContent}>{text}</Text>}
                    {messageContent && !text && messageContent()}
                  </View>
                ) : (
                  body()
                )}
              </View>
            </TouchableWithoutFeedback>
            <View style={styles.NbModalBottomOptions}>
              <Button
                onPress={() => onOk && onOk()}
                text={textOkButton || 'Ok'}
                id='okButton'
              />
              {onCancel && (
                <Button
                  onPress={() => onCancel()}
                  text={textCancelButton || 'Cancel'}
                  outline={true}
                  id='cancelButton'
                />
              )}
            </View>
          </View>
        </View>
      </TouchableWithoutFeedback>
    </Modal>
  );
};

export default React.memo(ModalAlert);
