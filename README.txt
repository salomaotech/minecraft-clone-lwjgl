MINECRAFT CLONE - versao LWJGL (convertida do JavaFX)
========================================================

O QUE MUDOU
------------
O projeto original usava JavaFX 3D (Box/Group/PhongMaterial/PointLight,
SubScene, Robot para travar o mouse) e controles JavaFX/Swing pra UI. Isso
foi TOTALMENTE substituido por LWJGL puro:

- Janela e input:      JavaFX Stage/Scene/Robot  ->  GLFW (janela + captura
                        nativa de mouse com GLFW_CURSOR_DISABLED, sem o hack
                        de recentralizar o cursor manualmente)
- Renderizacao 3D:     Box/PhongMaterial/PointLight (JavaFX)  ->  OpenGL
                        fixed-function (glBegin/glLight), que reproduz o
                        mesmo modelo de luz (ambiente + luz do sol/lua +
                        cor solida por bloco) sem precisar reescrever tudo
                        em shaders modernos
- HUD (toolbar/paleta/
  barra inferior):      HBox/Button/Label (JavaFX)  ->  overlay 2D proprio
                        desenhado com glOrtho + quads coloridos + texto via
                        stb_easy_font (biblioteca do LWJGL, sem imagem/fonte
                        externa)
- Dialogos Salvar/
  Carregar/Alertas:     FileChooser/Alert (JavaFX)  ->  JFileChooser/
                        JOptionPane (Swing, que ja vem no JDK e nao depende
                        de JavaFX nem do LWJGL)

O QUE FICOU IDENTICO (portado direto, mesma matematica)
---------------------------------------------------------
- Fisica: gravidade, pulo, sprint, colisao com blocos, "step up" de 1 bloco
- Raycast em grade pra mirar/quebrar/colocar bloco (mesmo alcance de 5 blocos)
- Cache de chunks em disco (chunk_cache/) pra nao estourar RAM
- Formato do arquivo de mundo (saves/mundo.txt) - 100% compativel com os
  mundos salvos pela versao antiga
- Ciclo de dia/noite (mesma duracao, mesmas cores, mesmo sol/lua quadrados)
- Todos os atalhos: WASD, SHIFT (sprint), SPACE/BACKSPACE (pular), scroll
  (trocar bloco), 1-9/0, T/Y (hora), N (noite), H (ajuda), R (reset),
  TAB/ESC (travar/destravar mouse), Ctrl+S / Ctrl+O (salvar/carregar)
- Botoes da toolbar: paleta de blocos, Plataforma, Casa, Torre, Limpar,
  Salvar, Carregar, Travar Mouse

A logica de jogo (World, Player, Raycast, DayNight) foi testada
isoladamente (fisica de queda, pouso sobre a plataforma, pulo, raycast
acertando o bloco certo, save/load preservando os blocos e o horario) e
todos os testes bateram com os valores esperados do projeto original.

COMO RODAR
-----------
Requer Maven e JDK 17+ (o pom compila com source/target 17; o JIT usado em
tempo de execucao vem do JVM instalado na maquina - JDK 21 ou newer tambem
funciona e traz otimizacoes ainda mais recentes).

Windows:  run.bat
Linux/Mac: ./run.sh

(equivalente a "mvn clean compile exec:java")

Pra gerar um .jar executavel: mvn clean package
  -> gera target/minecraft-clone-2.0-lwjgl.jar (com todas as dependencias
     dentro, "java -jar" funciona direto)

O pom.xml detecta seu sistema operacional automaticamente (Windows/Linux/
Mac) e baixa as natives certas do LWJGL na primeira compilacao.

LIMITACOES CONHECIDAS
-----------------------
- A UI usa OpenGL fixed-function (compatibilidade) em vez de shaders
  modernos - funciona perfeitamente pra um jogo de blocos como este, mas
  se um dia quiser modernizar pra shaders/instancing, o Renderer.java e
  o unico arquivo que precisa mudar.
- Os dialogos de Salvar/Carregar (Swing) abrem de forma nao-bloqueante
  (nao travam o jogo enquanto voce escolhe o arquivo) - na pratica isso e
  uma melhoria em relacao ao original, que travava a janela toda.
