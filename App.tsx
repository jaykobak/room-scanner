
import React, {useEffect} from 'react';
import {
  requireNativeComponent,
  PermissionsAndroid,
  Platform,
  SafeAreaView,
  Text,
  StyleSheet,
} from 'react-native';

const ArCoreView = requireNativeComponent('ArCoreView');

export default function App() {
  useEffect(() => {
    const requestCameraPermission = async () => {
      if (Platform.OS === 'android') {
        await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
        );
      }
    };

    requestCameraPermission();
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Room Scanner</Text>

      <ArCoreView style={styles.camera} />

      <Text style={styles.info}>ARCore view initialized</Text>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    alignItems: 'center',
  },
  title: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
    marginVertical: 20,
  },
  camera: {
    width: '100%',
    flex: 1,
  },
  info: {
    color: '#fff',
    padding: 16,
  },
});
