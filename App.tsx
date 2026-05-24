import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  BackHandler,
  FlatList,
  Modal,
  Pressable,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { WebView, WebViewNavigation } from 'react-native-webview';

type SearchEngine = 'google' | 'duckduckgo' | 'bing';

type HistoryItem = {
  id: string;
  url: string;
  timestamp: string;
};

const HOME_URL = 'https://www.google.com';
const DESKTOP_USER_AGENT =
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';

const searchEngineTemplates: Record<SearchEngine, string> = {
  google: 'https://www.google.com/search?q=',
  duckduckgo: 'https://duckduckgo.com/?q=',
  bing: 'https://www.bing.com/search?q=',
};

const App = (): React.JSX.Element => {
  const webViewRef = useRef<WebView>(null);

  const [currentUrl, setCurrentUrl] = useState<string>(HOME_URL);
  const [addressBarValue, setAddressBarValue] = useState<string>(HOME_URL);
  const [loading, setLoading] = useState<boolean>(false);
  const [progress, setProgress] = useState<number>(0);
  const [canGoBack, setCanGoBack] = useState<boolean>(false);
  const [canGoForward, setCanGoForward] = useState<boolean>(false);

  const [isSettingsOpen, setIsSettingsOpen] = useState<boolean>(false);
  const [isIncognito, setIsIncognito] = useState<boolean>(false);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [searchEngine, setSearchEngine] = useState<SearchEngine>('google');
  const [isDesktopMode, setIsDesktopMode] = useState<boolean>(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState<boolean>(false);

  const colors = useMemo(
    () => ({
      appBg: isIncognito ? '#0A0A0A' : '#1A1A1A',
      surface: isIncognito ? '#111111' : '#242424',
      surfaceSoft: isIncognito ? '#171717' : '#303030',
      text: '#FFFFFF',
      muted: '#B8B8B8',
      border: isIncognito ? '#222222' : '#3A3A3A',
      accent: '#2D8CFF',
      danger: '#FF4D4D',
    }),
    [isIncognito]
  );

  const normalizeInputToUrl = useCallback(
    (input: string): string => {
      const trimmed = input.trim();
      if (!trimmed) return currentUrl;

      const hasScheme = /^https?:\/\//i.test(trimmed);
      if (hasScheme) return trimmed;

      const looksLikeDomain = /^[^\s]+\.[^\s]+$/.test(trimmed) && !trimmed.includes(' ');
      if (looksLikeDomain) return `https://${trimmed}`;

      return `${searchEngineTemplates[searchEngine]}${encodeURIComponent(trimmed)}`;
    },
    [currentUrl, searchEngine]
  );

  const navigateTo = useCallback(
    (rawInput: string) => {
      const targetUrl = normalizeInputToUrl(rawInput);
      setCurrentUrl(targetUrl);
      setAddressBarValue(targetUrl);
    },
    [normalizeInputToUrl]
  );

  const handleNavigationStateChange = useCallback(
    (navState: WebViewNavigation) => {
      setCurrentUrl(navState.url);
      setAddressBarValue(navState.url);
      setCanGoBack(navState.canGoBack);
      setCanGoForward(navState.canGoForward);

      if (!isIncognito && navState.url && !navState.loading) {
        const now = new Date();
        const item: HistoryItem = {
          id: `${now.getTime()}-${navState.url}`,
          url: navState.url,
          timestamp: now.toLocaleString('tr-TR'),
        };

        setHistory(prev => {
          if (prev[0]?.url === item.url) return prev;
          return [item, ...prev].slice(0, 300);
        });
      }
    },
    [isIncognito]
  );

  const handleBackPress = useCallback(() => {
    if (isSettingsOpen) {
      setIsSettingsOpen(false);
      return true;
    }

    if (isHistoryOpen) {
      setIsHistoryOpen(false);
      return true;
    }

    if (canGoBack) {
      webViewRef.current?.goBack();
      return true;
    }

    return false;
  }, [canGoBack, isHistoryOpen, isSettingsOpen]);

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', handleBackPress);
    return () => sub.remove();
  }, [handleBackPress]);

  const openIncognito = useCallback(() => {
    setIsIncognito(true);
    setIsSettingsOpen(false);
    setIsHistoryOpen(false);
  }, []);

  const closeIncognito = useCallback(() => {
    setIsIncognito(false);
    setIsSettingsOpen(false);
  }, []);

  const clearHistory = useCallback(() => setHistory([]), []);

  return (
    <SafeAreaProvider>
      <SafeAreaView style={[styles.safeArea, { backgroundColor: colors.appBg }]}>
        <StatusBar barStyle="light-content" backgroundColor={colors.appBg} />

        <View style={[styles.container, { backgroundColor: colors.appBg }]}>
          <View style={[styles.header, { backgroundColor: colors.surface, borderBottomColor: colors.border }]}>
            <Pressable
              style={[styles.headerButton, { backgroundColor: colors.surfaceSoft, borderColor: colors.border }]}
              onPress={() => navigateTo(HOME_URL)}>
              <Text style={styles.headerButtonText}>🏠</Text>
            </Pressable>

            <View style={[styles.addressBarContainer, { backgroundColor: colors.surfaceSoft, borderColor: colors.border }]}>
              <TextInput
                value={addressBarValue}
                onChangeText={setAddressBarValue}
                onSubmitEditing={() => navigateTo(addressBarValue)}
                placeholder="Adres veya arama yaz..."
                placeholderTextColor={colors.muted}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="url"
                returnKeyType="go"
                style={[styles.addressBarInput, { color: colors.text }]}
              />
              {isIncognito ? <Text style={[styles.incognitoLabel, { color: colors.muted }]}>🕶 Gizli</Text> : null}
            </View>

            <Pressable
              style={[styles.headerButton, { backgroundColor: colors.surfaceSoft, borderColor: colors.border }]}
              onPress={() => setIsSettingsOpen(prev => !prev)}>
              <Text style={styles.headerButtonText}>⚙️</Text>
            </Pressable>
          </View>

          {loading ? <View style={[styles.progressBar, { width: `${Math.max(progress * 100, 4)}%`, backgroundColor: colors.accent }]} /> : null}

          <WebView
            ref={webViewRef}
            source={{ uri: currentUrl }}
            onLoadStart={() => setLoading(true)}
            onLoadProgress={event => setProgress(event.nativeEvent.progress)}
            onLoadEnd={() => setLoading(false)}
            onNavigationStateChange={handleNavigationStateChange}
            javaScriptEnabled={true}
            domStorageEnabled={true}
            allowsInlineMediaPlayback={true}
            userAgent={isDesktopMode ? DESKTOP_USER_AGENT : undefined}
            style={styles.webview}
          />

          <View style={[styles.footer, { backgroundColor: colors.surface, borderTopColor: colors.border }]}>
            <Pressable
              style={[styles.footerButton, { opacity: canGoBack ? 1 : 0.3 }]}
              disabled={!canGoBack}
              onPress={() => webViewRef.current?.goBack()}>
              <Text style={styles.footerButtonText}>GERİ</Text>
            </Pressable>

            <Pressable
              style={[styles.footerButton, { opacity: canGoForward ? 1 : 0.3 }]}
              disabled={!canGoForward}
              onPress={() => webViewRef.current?.goForward()}>
              <Text style={styles.footerButtonText}>İLERİ</Text>
            </Pressable>

            <Pressable style={styles.footerButton} onPress={() => webViewRef.current?.reload()}>
              <Text style={styles.footerButtonText}>YENİLE</Text>
            </Pressable>
          </View>

          {isSettingsOpen ? (
            <Pressable style={styles.settingsOverlay} onPress={() => setIsSettingsOpen(false)}>
              <View style={[styles.settingsMenu, { backgroundColor: colors.surface, borderColor: colors.border }]}>
                <Pressable
                  style={styles.settingsItem}
                  onPress={isIncognito ? closeIncognito : openIncognito}>
                  <Text style={[styles.settingsItemText, { color: colors.text }]}>
                    {isIncognito ? 'Gizli Modu Kapat' : 'Yeni Gizli Sekme'}
                  </Text>
                </Pressable>

                <Pressable
                  style={styles.settingsItem}
                  onPress={() => {
                    setIsHistoryOpen(true);
                    setIsSettingsOpen(false);
                  }}>
                  <Text style={[styles.settingsItemText, { color: colors.text }]}>Geçmiş (History)</Text>
                </Pressable>

                <View style={styles.settingsSwitchRow}>
                  <Text style={[styles.settingsItemText, { color: colors.text }]}>Masaüstü Sitesi</Text>
                  <Switch
                    value={isDesktopMode}
                    onValueChange={setIsDesktopMode}
                    trackColor={{ false: '#666', true: colors.accent }}
                    thumbColor="#fff"
                  />
                </View>

                <View style={styles.engineSection}>
                  <Text style={[styles.settingsItemText, { color: colors.text, marginBottom: 8 }]}>Arama Motoru Seçimi</Text>
                  <View style={styles.engineButtonsRow}>
                    {(['google', 'duckduckgo', 'bing'] as SearchEngine[]).map(engine => (
                      <Pressable
                        key={engine}
                        onPress={() => setSearchEngine(engine)}
                        style={[
                          styles.engineButton,
                          {
                            borderColor: searchEngine === engine ? colors.accent : colors.border,
                            backgroundColor: searchEngine === engine ? '#163E72' : colors.surfaceSoft,
                          },
                        ]}>
                        <Text style={[styles.engineButtonText, { color: colors.text }]}>
                          {engine === 'google' ? 'Google' : engine === 'duckduckgo' ? 'DuckDuckGo' : 'Bing'}
                        </Text>
                      </Pressable>
                    ))}
                  </View>
                </View>
              </View>
            </Pressable>
          ) : null}

          <Modal visible={isHistoryOpen} animationType="slide" transparent={true} onRequestClose={() => setIsHistoryOpen(false)}>
            <View style={styles.historyModalOverlay}>
              <View style={[styles.historyModalContainer, { backgroundColor: colors.surface, borderColor: colors.border }]}>
                <View style={styles.historyHeaderRow}>
                  <Text style={[styles.historyTitle, { color: colors.text }]}>Geçmiş</Text>
                  <Pressable onPress={() => setIsHistoryOpen(false)}>
                    <Text style={[styles.historyCloseText, { color: colors.muted }]}>Kapat</Text>
                  </Pressable>
                </View>

                <Pressable style={[styles.clearHistoryButton, { backgroundColor: colors.danger }]} onPress={clearHistory}>
                  <Text style={styles.clearHistoryButtonText}>Geçmişi Temizle</Text>
                </Pressable>

                <FlatList
                  data={history}
                  keyExtractor={item => item.id}
                  contentContainerStyle={styles.historyList}
                  ListEmptyComponent={<Text style={[styles.emptyHistoryText, { color: colors.muted }]}>Henüz geçmiş kaydı yok.</Text>}
                  renderItem={({ item }) => (
                    <Pressable
                      style={[styles.historyItem, { borderBottomColor: colors.border }]}
                      onPress={() => {
                        setIsHistoryOpen(false);
                        navigateTo(item.url);
                      }}>
                      <Text numberOfLines={1} style={[styles.historyUrlText, { color: colors.text }]}>{item.url}</Text>
                      <Text style={[styles.historyTimeText, { color: colors.muted }]}>{item.timestamp}</Text>
                    </Pressable>
                  )}
                />
              </View>
            </View>
          </Modal>
        </View>
      </SafeAreaView>
    </SafeAreaProvider>
  );
};

const styles = StyleSheet.create({
  safeArea: { flex: 1 },
  container: { flex: 1 },
  header: {
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderBottomWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  headerButton: {
    width: 42,
    height: 42,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  headerButtonText: { color: '#FFFFFF', fontSize: 18 },
  addressBarContainer: {
    flex: 1,
    minHeight: 45,
    maxHeight: 45,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
  },
  addressBarInput: { flex: 1, fontSize: 14, paddingVertical: 0 },
  incognitoLabel: { marginLeft: 6, fontSize: 12, fontWeight: '600' },
  progressBar: { height: 2 },
  webview: { flex: 1, backgroundColor: '#0A0A0A' },
  footer: {
    height: 56,
    borderTopWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
  },
  footerButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: '#2E2E2E',
  },
  footerButtonText: { color: '#FFFFFF', fontWeight: '700', letterSpacing: 0.3 },
  settingsOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-start',
    alignItems: 'flex-end',
    paddingTop: 62,
    paddingRight: 10,
    backgroundColor: 'rgba(0,0,0,0.15)',
  },
  settingsMenu: {
    width: 280,
    borderRadius: 12,
    borderWidth: 1,
    paddingVertical: 6,
    paddingHorizontal: 10,
  },
  settingsItem: { paddingVertical: 10 },
  settingsItemText: { fontSize: 14, fontWeight: '600' },
  settingsSwitchRow: {
    paddingVertical: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  engineSection: { paddingVertical: 8 },
  engineButtonsRow: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  engineButton: { borderWidth: 1, borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6 },
  engineButtonText: { fontSize: 12, fontWeight: '600' },
  historyModalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'flex-end',
  },
  historyModalContainer: {
    minHeight: '60%',
    maxHeight: '85%',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    borderWidth: 1,
    padding: 14,
  },
  historyHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  historyTitle: { fontSize: 20, fontWeight: '700' },
  historyCloseText: { fontSize: 15, fontWeight: '600' },
  clearHistoryButton: {
    marginTop: 10,
    marginBottom: 12,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
  },
  clearHistoryButtonText: { color: '#FFF', fontWeight: '700' },
  historyList: { paddingBottom: 40 },
  emptyHistoryText: { textAlign: 'center', marginTop: 24 },
  historyItem: { paddingVertical: 10, borderBottomWidth: 1 },
  historyUrlText: { fontSize: 13, fontWeight: '600' },
  historyTimeText: { fontSize: 12, marginTop: 4 },
});

export default App;
