# Rhino 1.8.0 完全移行計画

## 概要

カスタマイズされたRhinoファイルを削除し、最新のRhino 1.8.0 APIを使用してYUICompressorを完全に書き直します。

## 目標

1. ✅ **最新のRhino 1.8.0を使用**
2. ✅ **カスタムトークン（CONDCOMMENT, KEEPCOMMENT）の機能を維持**
3. ✅ **既存のテストをすべてパス**
4. ✅ **保守性の向上**

## 移行戦略

### Phase 1: 調査と設計（完了目標：このセッション）

#### 1.1 Rhino 1.8.0の新しいAPI構造を理解

**新しいコメント処理方法:**
- Rhino 1.8.0では`AstNode`に`getComments()`メソッドがある
- `Comment`クラスで各種コメントを表現
- `CommentType.BLOCK_COMMENT`, `CommentType.LINE`, `CommentType.JSDOC`などの型がある

**新しいパーサー使用方法:**
```java
CompilerEnvirons env = new CompilerEnvirons();
env.setRecordingComments(true);  // コメントを記録
env.setRecordingLocalJsDocComments(true);

Parser parser = new Parser(env);
AstRoot ast = parser.parse(reader, sourceURI, 1);
```

**ASTの走査:**
```java
ast.visit(new NodeVisitor() {
    @Override
    public boolean visit(AstNode node) {
        // ノードを処理
        return true;  // 子ノードも訪問
    }
});
```

#### 1.2 コメント保持機能の新しい設計

**現在のアプローチ（カスタムトークン）:**
- CONDCOMMENT (160): `/*@cc_on...@*/`
- KEEPCOMMENT (161): `/*!...*/`
- カスタムParserで特殊トークンとして認識

**新しいアプローチ（Rhino 1.8.0）:**
1. **標準のコメント記録機能を使用**
   ```java
   env.setRecordingComments(true);
   ```

2. **カスタムコメントフィルター**
   ```java
   class SpecialCommentFilter {
       boolean isKeepComment(Comment comment) {
           String text = comment.getValue();
           return text.startsWith("!");  // /*!...*/
       }

       boolean isConditionalComment(Comment comment) {
           String text = comment.getValue();
           return text.startsWith("@cc_on") ||
                  text.matches("@if\\s*\\(.*");  // /*@cc_on...@*/
       }
   }
   ```

3. **コメントを出力時に保持**
   - AST走査時にコメント位置を記録
   - 出力生成時に適切な位置にコメントを挿入

### Phase 2: 実装（完了目標：このセッション）

#### 2.1 pom.xmlの更新
```xml
<dependency>
    <groupId>org.mozilla</groupId>
    <artifactId>rhino</artifactId>
    <version>1.8.0</version>
</dependency>
```

#### 2.2 カスタマイズファイルの削除
- `/src/main/java/org/mozilla/javascript/` ディレクトリ全体を削除
- バックアップは`/backup/mozilla-javascript/`に既存

#### 2.3 新しいコメント処理クラスの作成

**ファイル: `src/main/java/com/yahoo/platform/yui/compressor/CommentPreserver.java`**
```java
package com.yahoo.platform.yui.compressor;

import org.mozilla.javascript.ast.Comment;
import java.util.*;

public class CommentPreserver {
    private List<PreservedComment> comments = new ArrayList<>();

    public static class PreservedComment {
        public final int position;
        public final String text;
        public final CommentType type;

        public enum CommentType {
            KEEP,      // /*!...*/
            CONDITIONAL // /*@cc_on...@*/
        }
    }

    public void analyzeComments(Set<Comment> comments) {
        // コメントを分析して保持すべきものを記録
    }

    public void insertComments(StringBuilder output) {
        // 出力にコメントを挿入
    }
}
```

#### 2.4 JavaScriptCompressorの書き直し

**主な変更点:**

1. **古いカスタムParserの削除**
   ```java
   // 削除: org.mozilla.javascript.Parser（カスタム版）
   // 使用: 標準のorg.mozilla.javascript.Parser
   ```

2. **新しいパース処理**
   ```java
   CompilerEnvirons env = new CompilerEnvirons();
   env.setRecordingComments(true);
   env.setRecordingLocalJsDocComments(true);
   env.setLanguageVersion(Context.VERSION_ES6);

   Parser parser = new Parser(env);
   AstRoot ast = parser.parse(in, sourceURI, 1);
   ```

3. **コメント抽出**
   ```java
   Set<Comment> comments = ast.getComments();
   CommentPreserver preserver = new CommentPreserver();
   preserver.analyzeComments(comments);
   ```

4. **AST走査と変数名の難読化**
   ```java
   ast.visit(new NodeVisitor() {
       @Override
       public boolean visit(AstNode node) {
           if (node instanceof Name) {
               // 変数名を処理
           } else if (node instanceof FunctionNode) {
               // 関数を処理
           }
           return true;
       }
   });
   ```

5. **出力生成**
   ```java
   String compressed = ast.toSource();
   preserver.insertComments(new StringBuilder(compressed));
   ```

### Phase 3: テストと検証

#### 3.1 既存テストの実行
```bash
mvn test
```

#### 3.2 手動テスト

**テストケース1: ライセンスコメントの保持**
```javascript
// input.js
/*!
 * jQuery v1.8.0
 * Copyright (c) 2012 jQuery Foundation
 */
function test() { return 42; }
```

期待される出力:
```javascript
/*! jQuery v1.8.0 Copyright (c) 2012 jQuery Foundation */
function test(){return 42;}
```

**テストケース2: IE条件付きコメント**
```javascript
// input.js
/*@cc_on
  @if (@_win32)
    alert('Windows');
  @end
@*/
```

期待される出力:
```javascript
/*@cc_on @if(@_win32)alert('Windows');@end @*/
```

## 実装の詳細

### 新しいファイル構造

```
src/main/java/com/yahoo/platform/yui/compressor/
├── Bootstrap.java (既存)
├── JavaScriptCompressor.java (大幅書き直し)
├── CommentPreserver.java (新規)
├── SpecialCommentAnalyzer.java (新規)
├── CssCompressor.java (既存、変更なし)
├── JarClassLoader.java (既存、変更なし)
├── ScriptOrFnScope.java (既存、変更なし)
├── JavaScriptIdentifier.java (既存、変更なし)
├── JavaScriptToken.java (既存、変更なし)
└── YUICompressor.java (既存、変更なし)
```

### 削除するファイル

```
src/main/java/org/mozilla/javascript/
├── Parser.java (削除)
├── Token.java (削除)
├── TokenStream.java (削除)
└── Decompiler.java (削除)
```

## リスク評価

### 高リスク
1. **コメント保持機能**: 新しい実装が正しく動作するか
2. **後方互換性**: 既存の利用者への影響

### 中リスク
1. **パフォーマンス**: 新しい実装が遅くないか
2. **エッジケース**: 特殊な構文の処理

### 低リスク
1. **基本的な圧縮機能**: Rhinoの標準機能で対応可能
2. **CSS圧縮**: 影響なし

## 成功基準

1. ✅ すべてのテストがパス
2. ✅ `/*!...*/` コメントが保持される
3. ✅ `/*@cc_on...@*/` コメントが保持される
4. ✅ 圧縮率が既存と同等以上
5. ✅ ビルドが成功

## ロールバック計画

失敗した場合:
1. `backup/mozilla-javascript/` からカスタムファイルを復元
2. Rhino 1.7R2に戻す
3. 別のアプローチを検討

## 次のステップ

1. ✅ この計画を承認
2. 🔄 Phase 2の実装を開始
3. ⏳ テストと検証
4. ⏳ ドキュメント更新
5. ⏳ リリース
