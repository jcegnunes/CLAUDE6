package com.example.domain

import com.example.data.model.EquipmentType
import com.example.data.model.NormCriterionEntity
import com.example.data.model.TestResult

data class NBR16295GloveLimits(
    val classe: String,
    val tensaoUsoMaxAC_kV: Double,
    val tensaoProvaAC_kV: Double,
    val tensaoRigidezAC_kV: Double,
    val limite280mm: Double?,
    val limite360mm: Double?,
    val limite410mm: Double?,
    val limite460mm: Double?
)

data class VisualChecklistItem(
    val id: String,
    val title: String,
    val description: String = "",
    val normRef: String = "",
    val isCritical: Boolean = true
)

object NormsEngine {

    fun getVisualChecklist(equipmentType: EquipmentType): List<VisualChecklistItem> {
        return when (equipmentType) {
            EquipmentType.LUVA_ISOLANTE -> listOf(
                VisualChecklistItem(
                    id = "luva_01",
                    title = "Inspeção por insuflação de ar e estanqueidade",
                    description = "Ausência de microfuros, perfurações, cortes, picotes mecânicos ou vazamento de ar pressurizado.",
                    normRef = "ABNT NBR 16295 item 8.2 / IEC 60903"
                ),
                VisualChecklistItem(
                    id = "luva_02",
                    title = "Superfície do elastômero e ozonólise",
                    description = "Superfície da borracha livre de ressecamento, trincas, estrias por ozônio (ozonólise) ou fissuras na dobra dos dedos.",
                    normRef = "ABNT NBR 16295 item 5.3"
                ),
                VisualChecklistItem(
                    id = "luva_03",
                    title = "Contaminação e agentes químicos condutivos",
                    description = "Isenção total de óleos, graxas, solventes, hidrocarbonetos ou agentes químicos agressivos.",
                    normRef = "ABNT NBR 16295 / NR-10"
                ),
                VisualChecklistItem(
                    id = "luva_04",
                    title = "Legibilidade das marcações e etiqueta de CA",
                    description = "Gravação indelével do fabricante, Certificado de Aprovação (CA), classe de tensão (00 a 4) e tamanho perfeitamente legíveis.",
                    normRef = "ABNT NBR 16295 item 7.1"
                ),
                VisualChecklistItem(
                    id = "luva_05",
                    title = "Elasticidade e integridade física do elastômero",
                    description = "Elasticidade e flexibilidade originais preservadas sem deformação plástica permanente, estufamentos ou endurecimento.",
                    normRef = "ABNT NBR 16295 / IEC 60903"
                ),
                VisualChecklistItem(
                    id = "luva_06",
                    title = "Comprimento e integridade da orla do punho",
                    description = "Orla e punho intactos sem desfiamentos ou cortes irregulares, com comprimento mínimo regulamentar preservado.",
                    normRef = "ABNT NBR 16295 Tabela 3"
                )
            )

            EquipmentType.MANGA_ISOLANTE -> listOf(
                VisualChecklistItem(
                    id = "manga_01",
                    title = "Inspeção visual da superfície interna e externa",
                    description = "Ausência de furos, rasgos, cortes, puncturas ou abrasão profunda em ambas as faces da borracha.",
                    normRef = "ABNT NBR 10624 / ASTM D1051"
                ),
                VisualChecklistItem(
                    id = "manga_02",
                    title = "Envelhecimento térmico e ozonólise",
                    description = "Elastômero flexível sem ressecamento, perda de elasticidade, trincas ou endurecimento precoce.",
                    normRef = "ASTM D1051"
                ),
                VisualChecklistItem(
                    id = "manga_03",
                    title = "Isenção de derivados de petróleo e graxas",
                    description = "Sem contaminação por óleo isolante, graxas condutivas, solventes ou compostos químicos.",
                    normRef = "ABNT NBR 10624"
                ),
                VisualChecklistItem(
                    id = "manga_04",
                    title = "Integridade das alças e botões de fixação",
                    description = "Botões isolantes, alças e suspensórios em perfeito estado de retenção mecânica e sem trincas.",
                    normRef = "ASTM D1051"
                ),
                VisualChecklistItem(
                    id = "manga_05",
                    title = "Marcações normativas de fabricante e CA",
                    description = "Identificação de fabricante, lote, CA e classe de isolamento perfeitamente nítidos.",
                    normRef = "ABNT NBR 10624"
                )
            )

            EquipmentType.MANTA_ISOLANTE -> listOf(
                VisualChecklistItem(
                    id = "manta_01",
                    title = "Inspeção minuciosa de faces e bordas",
                    description = "Ausência de furos, cortes, rasgos, puncturas por arames/pregos ou fendas em ambas as faces.",
                    normRef = "ASTM D1048 / NBR 10622"
                ),
                VisualChecklistItem(
                    id = "manta_02",
                    title = "Integridade elástica e efeito corona",
                    description = "Material elastomérico flexível sem rigidez excessiva, estrias térmicas, queimaduras de arco ou efeito corona.",
                    normRef = "ASTM D1048"
                ),
                VisualChecklistItem(
                    id = "manta_03",
                    title = "Isenção de contaminantes e solventes",
                    description = "Livre de impregnações de graxa, óleos minerais, ácidos ou agentes químicos agressivos.",
                    normRef = "ASTM D1048"
                ),
                VisualChecklistItem(
                    id = "manta_04",
                    title = "Integridade dos ilhoses e reforços",
                    description = "Ilhoses de fixação e bordas vulcanizadas íntegros, sem rasgos de tração, delaminação ou fendas.",
                    normRef = "ASTM D1048"
                ),
                VisualChecklistItem(
                    id = "manta_05",
                    title = "Identificação de classe e tensão de trabalho",
                    description = "Gravação indelével de classe de isolamento, tensão máxima de uso, CA e fabricante legíveis.",
                    normRef = "ASTM D1048"
                )
            )

            EquipmentType.TAPETE_ISOLANTE -> listOf(
                VisualChecklistItem(
                    id = "tapete_01",
                    title = "Superfície antiderrapante e integridade estrutural",
                    description = "Superfície estriada/antiderrapante íntegra, sem descolamento, furos, rasgos ou trincas passantes.",
                    normRef = "ASTM D178-22 / NBR 14039"
                ),
                VisualChecklistItem(
                    id = "tapete_02",
                    title = "Isenção de corpos estranhos incrustados",
                    description = "Ausência de partículas metálicas, limalhas, pedregulhos ou corpos estranhos condutores na borracha.",
                    normRef = "ASTM D178-22"
                ),
                VisualChecklistItem(
                    id = "tapete_03",
                    title = "Isenção de umidade e contaminantes condutivos",
                    description = "Livre de umidade profunda absorvida, óleos isolantes degradados ou compostos químicos condutivos.",
                    normRef = "ASTM D178-22"
                ),
                VisualChecklistItem(
                    id = "tapete_04",
                    title = "Espessura e flexibilidade sem quebra mecânica",
                    description = "Espessura uniforme e flexibilidade preservada sem fratura ou fissura ao dobramento em 180°.",
                    normRef = "ASTM D178-22"
                ),
                VisualChecklistItem(
                    id = "tapete_05",
                    title = "Marcação de classe e rastreabilidade",
                    description = "Identificação indelével de tensão de trabalho, classe dielétrica e fabricante perfeitamente legíveis.",
                    normRef = "ASTM D178-22"
                )
            )

            EquipmentType.BASTAO_MANOBRA -> listOf(
                VisualChecklistItem(
                    id = "bastao_01",
                    title = "Superfície do tubo em fibra de vidro (PRFV) com núcleo celular",
                    description = "Tubo em resina epóxi reforçada com fibra de vidro liso, brilhante, polido e sem delaminações, lascas, trincas ou bolhas.",
                    normRef = "ASTM F711 / IEC 60855"
                ),
                VisualChecklistItem(
                    id = "bastao_02",
                    title = "Vedação e estanqueidade do núcleo de espuma sintética",
                    description = "Núcleo de poliuretano expandido (espuma sintética) perfeitamente selado, sem vazios, infiltração de umidade interna ou absorção condutiva.",
                    normRef = "ASTM F711 / IEC 60855"
                ),
                VisualChecklistItem(
                    id = "bastao_03",
                    title = "Cabeçote universal e ferragens de conexão mecânica",
                    description = "Cabeçote universal metálico/isolante e ganchos de manobra firmes, sem folgas excessivas, trincas mecânicas ou corrosão.",
                    normRef = "IEC 60855 / ASTM F711"
                ),
                VisualChecklistItem(
                    id = "bastao_04",
                    title = "Limitador de mão (empunhadura) e anel de guarda",
                    description = "Limitador de empunhadura e anel de guarda posicionados na distância correta de segurança conforme projeto e norma.",
                    normRef = "IEC 60855 / NR-10"
                ),
                VisualChecklistItem(
                    id = "bastao_05",
                    title = "Limpeza superficial, camada protetora e hidrofobicidade",
                    description = "Superfície externa limpa e desengraxada, camada de verniz uniforme, sem fuligem, graxa ou resíduos condutivos de arco elétrico/trilhamento.",
                    normRef = "ASTM F711 / IEC 60855"
                ),
                VisualChecklistItem(
                    id = "bastao_06",
                    title = "Gravação indelével e ensaio dielétrico (50 kV / 150 mm)",
                    description = "Gravação indelével de conformidade, ensaio dielétrico em bancada (50 kV a 150 mm entre dois eletrodos de referência conforme ASTM F711 e IEC 60855), número de série, fabricante e data perfeitamente legíveis.",
                    normRef = "ASTM F711 / IEC 60855"
                )
            )

            EquipmentType.VARA_MANOBRA -> listOf(
                VisualChecklistItem(
                    id = "vara_01",
                    title = "Tubos seccionais telescópicos em fibra de vidro (PRFV)",
                    description = "Tubos telescópicos em PRFV sem fissuras longitudinais, lascas, trincas de impacto, delaminação ou estufamentos.",
                    normRef = "ASTM F711 / IEC 60855"
                ),
                VisualChecklistItem(
                    id = "vara_02",
                    title = "Botões de travamento (lock buttons) e mecanismos de retenção",
                    description = "Botões de pressão e pinos de trava com engate preciso, firme e com pressão de mola calibrada sem risco de recuo sob carga.",
                    normRef = "IEC 60855 / ASTM F711"
                ),
                VisualChecklistItem(
                    id = "vara_03",
                    title = "Deslizamento e alinhamento axial das seções",
                    description = "Deslizamento suave entre as seções telescópicas sem atrito abrasivo, folga radial prejudicial ou emperramento mecânico.",
                    normRef = "ASTM F711 / IEC 60855"
                ),
                VisualChecklistItem(
                    id = "vara_04",
                    title = "Sapata inferior de proteção e cabeçote superior",
                    description = "Tampa inferior protetora de borracha (sapata) e cabeçote superior íntegros e firmemente montados.",
                    normRef = "IEC 60855 / ASTM F711"
                ),
                VisualChecklistItem(
                    id = "vara_05",
                    title = "Superfície hidrofóbica e isenção de contaminantes",
                    description = "Camada de verniz poliuretano sem desgaste abrasivo, livre de poeira condutiva, graxa ou infiltração de umidade.",
                    normRef = "ASTM F711 / IEC 60855"
                ),
                VisualChecklistItem(
                    id = "vara_06",
                    title = "Rastreabilidade e dados nominais do fabricante",
                    description = "Gravação de modelo, comprimento estendido, número de seções, número de série e conformidade ASTM F711 / IEC 60855 (ensaio a 50 kV / 150 mm com dois eletrodos de referência) legíveis.",
                    normRef = "ASTM F711 / IEC 60855"
                )
            )

            // aterramento temporário: mesmos itens da web (chk-at1..6)
            EquipmentType.ATERRAMENTO_COM_BASTAO, EquipmentType.ATERRAMENTO_SEM_BASTAO -> listOfNotNull(
                VisualChecklistItem(id = "chk-at1", title = "Grampos de linha e de terra sem trincas, com molas, parafusos e mordentes íntegros", normRef = "NR-10"),
                VisualChecklistItem(id = "chk-at2", title = "Cabos de aterramento sem fios rompidos, emendas ou capa isolante danificada", normRef = "NR-10"),
                VisualChecklistItem(id = "chk-at3", title = "Terminais e conexões prensados, firmes e sem corrosão", normRef = "NR-10"),
                VisualChecklistItem(id = "chk-at4", title = "Grampo / trado de terra e haste em bom estado", normRef = "NR-10"),
                if (equipmentType == EquipmentType.ATERRAMENTO_COM_BASTAO)
                    VisualChecklistItem(id = "chk-at5", title = "Bastão isolante sem trincas, delaminação ou umidade; cabeçote e encaixes firmes", normRef = "ABNT NBR 11854")
                else VisualChecklistItem(id = "chk-at5", title = "Identificação do fabricante, seção do cabo e tensão nominal legíveis", normRef = "NR-10"),
                if (equipmentType == EquipmentType.ATERRAMENTO_COM_BASTAO)
                    VisualChecklistItem(id = "chk-at6", title = "Identificação da tensão nominal e limites de empunhadura do bastão visíveis", normRef = "ABNT NBR 11854")
                else null
            )

            EquipmentType.ATERRAMENTO_TEMPORARIO -> listOf(
                VisualChecklistItem(
                    id = "at_01",
                    title = "Cordoalhas e cabos de cobre extraflexível",
                    description = "Cabos de aterramento em cobre eletrolítico com capa de PVC/silicone transparente íntegra, sem condutores rompidos, esmagamentos, oxidação ou cortes.",
                    normRef = "ABNT NBR IEC 61230 / ASTM F855"
                ),
                VisualChecklistItem(
                    id = "at_02",
                    title = "Grampos de fixação às fases e ao terra",
                    description = "Grampos em liga de alumínio ou bronze sem trincas, empenamentos, dentes desgastados, parafusos espanados ou folgas nos mordentes.",
                    normRef = "ABNT NBR IEC 61230"
                ),
                VisualChecklistItem(
                    id = "at_03",
                    title = "Bloco de derivação e conexões intermediárias",
                    description = "Bloco conector central íntegro, estanque, com conexões firmemente torqueadas e sem pontos quentes ou folga mecânica.",
                    normRef = "ABNT NBR IEC 61230 / ASTM F855"
                ),
                VisualChecklistItem(
                    id = "at_04",
                    title = "Bastões isolantes de manobra / punhos isolados",
                    description = "Bastões acopláveis em fibra de vidro (PRFV) lisos, secos, sem fissuras, delaminações, quebras ou contaminações superficiais condutivas.",
                    normRef = "ABNT NBR 16613 / ASTM F711"
                ),
                VisualChecklistItem(
                    id = "at_05",
                    title = "Terminais de prensagem e sapatas de fixação",
                    description = "Terminais de compressão com prensagem perfeita, sem fios soltos na virola e com proteção elástica/reforço contra dobramento.",
                    normRef = "ABNT NBR IEC 61230"
                ),
                VisualChecklistItem(
                    id = "at_06",
                    title = "Identificação de bitola, Icc nominal e rastreabilidade",
                    description = "Gravação legível da seção dos cabos (ex: 25, 35, 50, 70 ou 95 mm²), capacidade nominal de curto-circuito (Icc/tempo), TAG e fabricante.",
                    normRef = "ABNT NBR IEC 61230 / NR-10 item 10.5.1"
                )
            )

            EquipmentType.FERRAMENTA_ISOLADA -> listOf(
                VisualChecklistItem(
                    id = "ferramenta_01",
                    title = "Capa isolante de proteção (isolamento 1000V)",
                    description = "Camada isolante sem cortes, trincas, quebras, fissuras, perfurações mecânicas ou desgaste por abrasão.",
                    normRef = "ABNT NBR 9699 / IEC 60900"
                ),
                VisualChecklistItem(
                    id = "ferramenta_02",
                    title = "Aderência da capa ao corpo condutor",
                    description = "Aderência total do revestimento isolante ao corpo metálico (sem folgas axiais, giro ou descolamento).",
                    normRef = "IEC 60900 item 5.4"
                ),
                VisualChecklistItem(
                    id = "ferramenta_03",
                    title = "Guarda protetora de mão (anteparo isolante)",
                    description = "Guarda de proteção (anteparo) íntegra para evitar deslizamento da mão do operador em direção a partes condutoras vivas.",
                    normRef = "ABNT NBR 9699 item 4.2"
                ),
                VisualChecklistItem(
                    id = "ferramenta_04",
                    title = "Articulações, catracas e mecanismos móveis",
                    description = "Mecanismos articulados (alicates, chaves catraca e soquetes) com movimento suave e sem folga excessiva.",
                    normRef = "IEC 60900"
                ),
                VisualChecklistItem(
                    id = "ferramenta_05",
                    title = "Símbolo de duplo triângulo 1000V e norma",
                    description = "Gravação obrigatória do símbolo oficial de duplo triângulo 1000V, IEC 60900 / ABNT NBR 9699 e fabricante.",
                    normRef = "ABNT NBR 9699 item 6"
                ),
                VisualChecklistItem(
                    id = "ferramenta_06",
                    title = "Partes metálicas ativas e mordentes",
                    description = "Pontas e mordentes de trabalho sem oxidação profunda, dentes espanados ou deformações térmicas.",
                    normRef = "IEC 60900"
                )
            )

            EquipmentType.ESCADA_ISOLADA -> listOf(
                VisualChecklistItem(
                    id = "escada_01",
                    title = "Montantes em fibra de vidro (PRFV)",
                    description = "Perfis estruturais em PRFV sem trincas, quebras, lascas, furos não originais ou delaminação de resina.",
                    normRef = "EN 50528:2024 / NBR IEC 61478"
                ),
                VisualChecklistItem(
                    id = "escada_02",
                    title = "Degraus estriados antiderrapantes e fixações",
                    description = "Degraus de alumínio/fibra estriados firmemente cravados nos montantes, sem rotação, trincas ou afrouxamento.",
                    normRef = "ANSI A14.5 / EN 50528"
                ),
                VisualChecklistItem(
                    id = "escada_03",
                    title = "Sapatas articuladas de borracha antiderrapante",
                    description = "Sapatas com perfil de borracha antiderrapante íntegras, sem desgaste excessivo, rasgos ou descolamento.",
                    normRef = "EN 50528:2024"
                ),
                VisualChecklistItem(
                    id = "escada_04",
                    title = "Catracas de extensão, roldanas e corda sintética",
                    description = "Mecanismo de extensão, roldanas e corda de polipropileno/náilon sem nós, desfiamentos ou travamentos mecânicos.",
                    normRef = "ANSI A14.5"
                ),
                VisualChecklistItem(
                    id = "escada_05",
                    title = "Isenção de contaminações condutivas nos perfis",
                    description = "Isenção total de tintas condutoras, fuligem, graxas condutivas ou umidade retida no interior dos montantes.",
                    normRef = "ABNT NBR IEC 61478"
                ),
                VisualChecklistItem(
                    id = "escada_06",
                    title = "Etiquetas de advertência, carga e isolamento",
                    description = "Etiquetas de carga máxima de trabalho, ângulo de uso e isolamento elétrico legíveis.",
                    normRef = "EN 50528"
                )
            )

            EquipmentType.CAPACETE_CLASSE_B -> listOf(
                VisualChecklistItem(
                    id = "capacete_01",
                    title = "Casco rígido em polímero e integridade",
                    description = "Casco em polietileno de alta densidade sem trincas, fissuras, perfurações ou deformações térmicas.",
                    normRef = "ABNT NBR 8221 / ANSI Z89.1"
                ),
                VisualChecklistItem(
                    id = "capacete_02",
                    title = "Ausência de furos e condutores estranhos",
                    description = "Ausência absoluta de orifícios de ventilação, rebites/parafusos adicionais ou adesivos condutivos metálicos.",
                    normRef = "ABNT NBR 8221 Classe B"
                ),
                VisualChecklistItem(
                    id = "capacete_03",
                    title = "Suspensão interna (carneira), coroa e jugular",
                    description = "Carneira plástica, tiras têxteis da coroa e jugular íntegras e firmemente fixadas aos pontos de ancoragem do casco.",
                    normRef = "ABNT NBR 8221"
                ),
                VisualChecklistItem(
                    id = "capacete_04",
                    title = "Espaçamento regulamentar de amortecimento",
                    description = "Distância regulamentar de amortecimento de impacto entre a carneira e o ápice do casco preservada.",
                    normRef = "ABNT NBR 8221"
                ),
                VisualChecklistItem(
                    id = "capacete_05",
                    title = "Marcação de Classe B (Classe 2 - 20 kV) e CA",
                    description = "Gravação indelével de Classe B (20 kV), CA, fabricante e data de fabricação/validade legíveis.",
                    normRef = "ABNT NBR 8221"
                )
            )

            EquipmentType.BOTA_DIELETRICA -> listOf(
                VisualChecklistItem(
                    id = "bota_01",
                    title = "Solado de borracha vulcanizada de alta isolação",
                    description = "Solado isolante íntegro, com relevo antiderrapante e sem desgaste até a entressola, cortes ou perfurações por pregos.",
                    normRef = "ABNT NBR 16603 / ASTM F2413"
                ),
                VisualChecklistItem(
                    id = "bota_02",
                    title = "Cabedal em couro/polímero e costuras",
                    description = "Cabedal sem rasgos, cortes profundos, descosturas ou descolamento na junção com o solado.",
                    normRef = "ASTM F2413"
                ),
                VisualChecklistItem(
                    id = "bota_03",
                    title = "Biqueira de segurança não-metálica (composite)",
                    description = "Biqueira de segurança não-metálica (composite/fibra de carbono) íntegra, sem trincas mecânicas ou exposição.",
                    normRef = "ABNT NBR 16603"
                ),
                VisualChecklistItem(
                    id = "bota_04",
                    title = "Ausência de componentes condutores metálicos",
                    description = "Ausência total de pregos, ilhoses condutivos metálicos ou elementos passíveis de condução elétrica no calçado.",
                    normRef = "ABNT NBR 16603"
                ),
                VisualChecklistItem(
                    id = "bota_05",
                    title = "Higienização interna e isenção de umidade",
                    description = "Interior do calçado limpo, seco e livre de pós condutivos, limalhas ou umidade.",
                    normRef = "ABNT NBR 16603"
                ),
                VisualChecklistItem(
                    id = "bota_06",
                    title = "Marcação de CA e símbolo dielétrico",
                    description = "Gravação de Certificado de Aprovação (CA) para risco elétrico e simbologia de calçado dielétrico legíveis.",
                    normRef = "ABNT NBR 16603"
                )
            )

            EquipmentType.DETECTOR_TENSAO -> listOf(
                VisualChecklistItem(
                    id = "detec_01",
                    title = "Carcaça plástica isolante e blindagem",
                    description = "Carcaça isolante sem trincas, quebras, fissuras, deformações ou penetração de umidade interna.",
                    normRef = "ABNT NBR IEC 61243-1"
                ),
                VisualChecklistItem(
                    id = "detec_02",
                    title = "Ponta de prova / gancho de contato frontal",
                    description = "Eletrodo de contato frontal limpo, sem oxidação severa, folga na fixação mecânica ou deformações.",
                    normRef = "ABNT NBR IEC 61243-1"
                ),
                VisualChecklistItem(
                    id = "detec_03",
                    title = "Circuito de autoteste (Self-Test / Built-in)",
                    description = "Circuito de autoteste operacional com sinalização acústica (buzzer) e óptica (LEDs) perfeitamente audíveis e visíveis.",
                    normRef = "ABNT NBR IEC 61243-1 item 5.2"
                ),
                VisualChecklistItem(
                    id = "detec_04",
                    title = "Compartimento de bateria e estanqueidade",
                    description = "Compartimento de baterias estanque, com contatos limpos e livre de corrosão química por vazamento de pilhas.",
                    normRef = "ABNT NBR IEC 61243-1"
                ),
                VisualChecklistItem(
                    id = "detec_05",
                    title = "Encaixe universal para bastão de manobra",
                    description = "Adaptador universal ranhurado para engate em bastão de manobra firme, sem trincas e com rosca de aperto íntegra.",
                    normRef = "ABNT NBR IEC 61243-1"
                ),
                VisualChecklistItem(
                    id = "detec_06",
                    title = "Faixa de tensão nominal e frequência de operação",
                    description = "Indicação clara da faixa de tensão nominal de trabalho (ex: 1 kV a 36 kV), frequência e fabricante legíveis.",
                    normRef = "ABNT NBR IEC 61243-1"
                )
            )

            EquipmentType.DETECTOR_INDUCAO -> listOf(
                VisualChecklistItem(id = "chk-di1", title = "Carcaça / invólucro sem trincas, quebras ou partes soltas", normRef = "Especificações do fabricante"),
                VisualChecklistItem(id = "chk-di2", title = "Autoteste: sinalização luminosa e sonora funcionando", normRef = "Especificações do fabricante"),
                VisualChecklistItem(id = "chk-di3", title = "Pilhas / bateria em bom estado e contatos sem oxidação", normRef = "Especificações do fabricante"),
                VisualChecklistItem(id = "chk-di4", title = "Botão liga/desliga e seletor de sensibilidade funcionando", normRef = "Especificações do fabricante"),
                VisualChecklistItem(id = "chk-di5", title = "Identificação do fabricante, modelo e faixa de tensão legíveis", normRef = "Especificações do fabricante")
            )

            EquipmentType.PONTEIRA_PROVA -> listOf(
                VisualChecklistItem(
                    id = "pont_01",
                    title = "Cabos de teste com dupla isolação flexível",
                    description = "Cabos com dupla camada isolante em silicone sem cortes, esmagamentos, nós ou alma condutora de cobre exposta.",
                    normRef = "IEC 61010-031"
                ),
                VisualChecklistItem(
                    id = "pont_02",
                    title = "Barreira protetora para os dedos (finger guard)",
                    description = "Corpo da ponteira com anteparo/guarda de proteção para os dedos intacto e sem trincas.",
                    normRef = "IEC 61010-031"
                ),
                VisualChecklistItem(
                    id = "pont_03",
                    title = "Conectores banana de segurança blindados",
                    description = "Conectores tipo banana blindados sem folga no encaixe, trincas nas luvas retráteis ou pinos amassados.",
                    normRef = "IEC 61010-031"
                ),
                VisualChecklistItem(
                    id = "pont_04",
                    title = "Categoria de sobretensão (CAT III / CAT IV)",
                    description = "Gravação indelével da categoria de sobretensão (CAT III/IV), tensão máxima e corrente de pico legíveis.",
                    normRef = "IEC 61010-031"
                )
            )

            EquipmentType.OUTRO -> listOf(
                VisualChecklistItem(
                    id = "outro_01",
                    title = "Inspeção visual estrutural e acabamento",
                    description = "Ausência de danos físicos, trincas, fissuras, cortes ou quebras na superfície isolante do dispositivo.",
                    normRef = "NR-10 item 10.4.3.1"
                ),
                VisualChecklistItem(
                    id = "outro_02",
                    title = "Limpeza e isenção de contaminantes condutivos",
                    description = "Superfície isolante limpa, desengraxada, seca e livre de pós condutivos, graxas ou umidade.",
                    normRef = "NR-10"
                ),
                VisualChecklistItem(
                    id = "outro_03",
                    title = "Identificação e dados de placa do fabricante",
                    description = "Identificação do fabricante, modelo, características elétricas nominais e rastreabilidade legíveis.",
                    normRef = "NR-10"
                ),
                VisualChecklistItem(
                    id = "outro_04",
                    title = "Condições físico-mecânicas para ensaio dielétrico",
                    description = "Condições mecânicas e estruturais adequadas para suportabilidade dielétrica sem risco de colapso físico.",
                    normRef = "NR-10 item 10.7.8"
                )
            )
        }
    }

    val tabela4NBR16295 = listOf(
        NBR16295GloveLimits("00", 0.5, 2.5, 5.0, 10.0, 12.0, null, null),
        NBR16295GloveLimits("0", 1.0, 5.0, 10.0, 10.0, 12.0, 14.0, 16.0),
        NBR16295GloveLimits("1", 7.5, 10.0, 20.0, null, 14.0, 16.0, 18.0),
        NBR16295GloveLimits("2", 17.0, 20.0, 30.0, null, 16.0, 18.0, 20.0),
        NBR16295GloveLimits("3", 26.5, 30.0, 40.0, null, 18.0, 20.0, 22.0),
        NBR16295GloveLimits("4", 36.0, 40.0, 50.0, null, null, 22.0, 24.0)
    )

    /** Patamar da Tabela 4 (280, 360, 410, 460) — mesma regra da web (getClosestGloveLength). */
    fun closestGloveLength(length_mm: Int): Int = when {
        length_mm <= 300 -> 280
        length_mm <= 380 -> 360
        length_mm <= 430 -> 410
        else -> 460
    }

    /**
     * Limite unitário (1 luva) da Tabela 4 da NBR 16295, ou null quando o
     * comprimento não é aplicável à classe (N/a) — igual à web.
     */
    fun gloveUnitLimitOrNull(dielectricClass: String, length_mm: Int): Double? {
        val cleanClass = dielectricClass.replace("Classe", "", ignoreCase = true).trim().uppercase()
        val entry = tabela4NBR16295.find { it.classe.uppercase() == cleanClass } ?: return null
        return when (closestGloveLength(length_mm)) {
            280 -> entry.limite280mm
            360 -> entry.limite360mm
            410 -> entry.limite410mm
            else -> entry.limite460mm
        }
    }

    /** Limite unitário para exibição (0.0 quando N/a). */
    fun getGloveUnitaryLeakageLimit(dielectricClass: String, length_mm: Int): Double =
        gloveUnitLimitOrNull(dielectricClass, length_mm) ?: 0.0

    /** Limite do ensaio simultâneo de 2 luvas (dobro da Tabela 4); 0.0 quando N/a. */
    fun getGloveMaxLeakageCurrent(dielectricClass: String, length_mm: Int, simultaneousGloves: Int = 2): Double =
        getGloveUnitaryLeakageLimit(dielectricClass, length_mm) * simultaneousGloves


    data class StickLeakageNormItem(
        val diameter_mm: Int,
        val limit_50kV_uA: Int,
        val limit_100kV_uA: Int
    )

    val tabelaFugaBastoes = listOf(
        StickLeakageNormItem(32, 5, 10),
        StickLeakageNormItem(38, 6, 12),
        StickLeakageNormItem(51, 8, 15),
        StickLeakageNormItem(64, 10, 20)
    )

    fun getStickMaxLeakage_uA(diameter_mm: Int, voltage_kV: Double = 50.0): Int {
        val item = when {
            diameter_mm <= 32 -> tabelaFugaBastoes[0]
            diameter_mm <= 38 -> tabelaFugaBastoes[1]
            diameter_mm <= 51 -> tabelaFugaBastoes[2]
            else -> tabelaFugaBastoes[3]
        }
        return if (voltage_kV >= 75.0) item.limit_100kV_uA else item.limit_50kV_uA
    }

    // =====================================================================
    // CRITÉRIOS E AVALIAÇÃO — mesma lógica da plataforma web
    // (src/services/normsEngine.ts: findMatchingCriterion + evaluateDielectricTest)
    // A base de cálculo é a lista de critérios da web (NormsCatalog), não
    // valores fixos no aplicativo.
    // =====================================================================

    /** Limite de fuga adotado para tapetes (ASTM D178-22 não estipula valor). */
    const val TAPETE_LEAKAGE_LIMIT_MA = 100.0

    /** Escadas: método padrão da web (segmento 300 mm / 100 kV) — 0,10 mA seco, 0,20 mA úmido. */
    const val LADDER_DEFAULT_LIMIT_MA = 0.10
    /** Critério padrão da escada: EN 50528:2024 (baixa tensão, 36 kV CA, fuga 0,50 mA). */
    const val LADDER_DEFAULT_CRITERION_ID = "norm-escada-en-50528-2024-bt-36kv"

    /** Critério de escada de um ensaio salvo (o app não grava o id): mesma norma e tensão. */
    fun ladderCriterionIdFor(normCode: String, appliedVoltage_kV: Double): String? =
        criteriaFor(EquipmentType.ESCADA_ISOLADA).firstOrNull {
            it.normCode.equals(normCode.trim(), ignoreCase = true) && kotlin.math.abs(it.testVoltage_kV - appliedVoltage_kV) < 0.5
        }?.id
    const val LADDER_DEFAULT_CLAUSES = "ABNT NBR IEC 61478 (Item 6.3) / ABNT NBR 16308"

    // ABNT NBR 11854 — bastões de manobra, varas telescópicas e bastões de resgate:
    // corrente de fuga máxima (µA) pelo diâmetro, ensaio a 50 kV (igual à web v7.4)
    const val NBR11854_TEST_VOLTAGE_KV = 50.0

    // ABNT NBR 6881 — ponteiras de prova e cabos de ensaio (até 1 kV): rampa a partir de no máx. 20 % da nominal
    const val PROBE_NOMINAL_VOLTAGE_KV = 1.0
    const val PROBE_RAMP_START_MAX_KV = 0.2 * PROBE_NOMINAL_VOLTAGE_KV
    private val NBR11854_POLE_DIAMETERS_MM = listOf(32, 38, 51, 64)
    const val NBR11854_DEFAULT_DIAMETER_MM = 32

    /** Diâmetro da tabela mais próximo do informado (32, 38, 51 ou 64 mm); sem valor: 32 mm. */
    fun closestPoleDiameter(diameter_mm: Int?): Int {
        if (diameter_mm == null || diameter_mm <= 0) return NBR11854_DEFAULT_DIAMETER_MM
        return NBR11854_POLE_DIAMETERS_MM.fold(NBR11854_DEFAULT_DIAMETER_MM) { best, x ->
            if (kotlin.math.abs(x - diameter_mm) < kotlin.math.abs(best - diameter_mm)) x else best
        }
    }

    /** Limite (µA) da NBR 11854 a 50 kV para o diâmetro do bastão. */
    fun nbr11854LeakageLimit_uA(diameter_mm: Int?): Double {
        val d = closestPoleDiameter(diameter_mm)
        return (tabelaFugaBastoes.find { it.diameter_mm == d } ?: tabelaFugaBastoes[0]).limit_50kV_uA.toDouble()
    }

    private fun uaStr(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    private fun fmt(v: Double, decimals: Int = 1): String = String.format(java.util.Locale.US, "%.${decimals}f", v)

    private fun cleanClass(dielectricClass: String) = dielectricClass.replace("Classe", "", ignoreCase = true).trim()

    /** Texto do limite do bastão/vara: "N µA (P pontos ensaiados × X µA da tabela ...)". */
    private fun stickLimitText(limit: Double, input: EvaluationInput): String {
        val d = closestPoleDiameter(input.stickDiameter_mm)
        val points = input.stickTestPoints.coerceAtLeast(1)
        val table = "da tabela da ABNT NBR 11854 para bastão de Ø $d mm a ${NBR11854_TEST_VOLTAGE_KV.toInt()} kV"
        return if (points > 1) "${uaStr(limit)} µA ($points pontos ensaiados × ${uaStr(nbr11854LeakageLimit_uA(d))} µA $table)"
        else "${uaStr(limit)} µA $table"
    }

    /** Escada: " — P pontos ensaiados × X mA da norma" (vazio com 1 ponto). */
    private fun ladderPointsText(limit: Double, input: EvaluationInput): String {
        val points = input.stickTestPoints.coerceAtLeast(1)
        return if (points > 1) " — $points pontos ensaiados × ${fmt(limit / points, 2)} mA da norma" else ""
    }

    /** Bastão / vara de manobra e aterramento temporário com bastão: NBR 11854, limite em µA pelo diâmetro. */
    fun isStickType(type: EquipmentType) =
        type == EquipmentType.BASTAO_MANOBRA || type == EquipmentType.VARA_MANOBRA || type == EquipmentType.ATERRAMENTO_COM_BASTAO

    private fun isStick(type: EquipmentType) = isStickType(type)

    /**
     * Tipo cujos critérios da lista valem para o equipamento (como na web): aterramento com bastão
     * usa os da vara de manobra e o sem bastão os da ponteira de prova.
     */
    fun criteriaWebType(type: EquipmentType): String = when (type) {
        EquipmentType.ATERRAMENTO_COM_BASTAO -> "vara_manobra"
        EquipmentType.ATERRAMENTO_SEM_BASTAO -> "ponteira_prova"
        else -> webType(type)
    }

    /** Tipo do equipamento no formato da web (applicableEquipmentTypes). */
    fun webType(type: EquipmentType): String = when (type) {
        EquipmentType.BOTA_DIELETRICA -> "bota_dielétrica"
        else -> type.name.lowercase()
    }

    /** Critérios da lista que se aplicam ao tipo (para o seletor de norma do ensaio). */
    fun criteriaFor(type: EquipmentType): List<WebNormCriterion> =
        NormsCatalog.criteria.filter { criteriaWebType(type) in it.applicableEquipmentTypes }

    /**
     * findMatchingCriterion da web: critério escolhido (se aplicável ao tipo) ou o
     * primeiro ativo com o mesmo tipo e classe ("Geral" serve para qualquer classe).
     * Como o assistente da web: ferramentas usam a classe "0" e tapete classe "00" usa "0".
     */
    fun findCriterion(
        type: EquipmentType,
        dielectricClass: String,
        criterionId: String? = null,
        all: List<WebNormCriterion> = NormsCatalog.criteria
    ): WebNormCriterion? {
        val wt = criteriaWebType(type)
        if (!criterionId.isNullOrBlank()) {
            all.find { it.id == criterionId && it.isActive && wt in it.applicableEquipmentTypes }?.let { return it }
        }
        // escada: padrão EN 50528:2024 — baixa tensão, 36 kV CA (como na web)
        if (type == EquipmentType.ESCADA_ISOLADA) {
            all.find { it.id == LADDER_DEFAULT_CRITERION_ID && it.isActive && wt in it.applicableEquipmentTypes }?.let { return it }
        }
        val raw = cleanClass(dielectricClass)
        val inputClass = when {
            type == EquipmentType.FERRAMENTA_ISOLADA -> "0"
            type == EquipmentType.TAPETE_ISOLANTE && raw == "00" -> "0"
            else -> raw
        }.lowercase()
        val byClass = all.firstOrNull { c ->
            if (!c.isActive) return@firstOrNull false
            val typeMatches = wt in c.applicableEquipmentTypes
            val normClass = c.dielectricClass.trim().lowercase()
            var classMatches = normClass == inputClass || normClass == "geral"
            if (type == EquipmentType.CAPACETE_CLASSE_B) {
                if ((inputClass == "2" || inputClass == "b" || inputClass.contains("2") || inputClass.contains("b")) &&
                    (normClass == "2" || normClass == "b" || normClass.contains("2") || normClass.contains("b") || normClass == "geral")
                ) classMatches = true
            }
            if (type == EquipmentType.FERRAMENTA_ISOLADA) {
                if (normClass == "1000v" || normClass == "1000 v" || normClass == "1000" || normClass == "0" ||
                    normClass == "geral" || normClass.contains("1000") ||
                    inputClass == "0" || inputClass == "00" || inputClass == "1000v" || inputClass == "1000" || inputClass == "geral"
                ) classMatches = true
                if (typeMatches) classMatches = true
            }
            typeMatches && classMatches
        }
        if (byClass != null) return byClass
        // Tipo com uma única norma cadastrada e classe não numérica (ex.: calçado "Especial"):
        // ela é carregada ao selecionar o equipamento. Tipos com norma por classe (ex.: manga
        // classe 2) ou com várias normas (detectores) continuam exigindo a escolha no ensaio.
        val forType = all.filter { it.isActive && wt in it.applicableEquipmentTypes }
        val single = forType.singleOrNull() ?: return null
        return if (single.dielectricClass.trim() !in setOf("00", "0", "1", "2", "3", "4")) single else null
    }

    data class StandardNormCriteria(
        val normCode: String,
        val targetVoltage_kV: Double,
        val durationSeconds: Int,
        /** Limite aplicado. Bastões/varas: em µA (unidade usada nas telas do app). */
        val leakageLimit_mA: Double,
        val voltageType: String = "AC",
        val retestIntervalMonths: Int = 6,
        val physicalRequirement: String = "Suportar sem disrupção ou perfuração dielétrica",
        val description: String = "",
        val currentUnit: String = "mA",
        val criterionId: String = "",
        val normName: String = "",
        /** false: nenhum critério da lista serve (a web não avalia: "Critério técnico não configurado"). */
        val configured: Boolean = true,
        /** Luvas: comprimento aplicável à classe na Tabela 4. */
        val gloveLengthApplicable: Boolean = true,
        val normReferenceClause: String = normCode
    )

    /** Limite aplicado pela web para o critério (luvas: Tabela 4 × 2; tapete 100 mA; escada; ferramentas). */
    private fun appliedLimit(
        type: EquipmentType,
        criterion: WebNormCriterion,
        dielectricClass: String,
        gloveLength_mm: Int,
        toolsCount: Int,
        stickDiameter_mm: Int? = null,
        stickTestPoints: Int = 1
    ): Triple<Double, Boolean, String> = when (type) {
        EquipmentType.LUVA_ISOLANTE -> {
            val unit = gloveUnitLimitOrNull(dielectricClass, gloveLength_mm)
            if (unit != null) Triple(unit * 2.0, true, "ABNT NBR 16295:2023 Tabela 4 (2 Luvas Simultâneas)")
            else Triple(criterion.maxLeakageCurrent, false, "ABNT NBR 16295:2023 Tabela 4 (2 Luvas Simultâneas)")
        }
        // NBR 11854: limite em µA pelo diâmetro do bastão (a corrente medida é digitada em µA)
        EquipmentType.BASTAO_MANOBRA, EquipmentType.VARA_MANOBRA, EquipmentType.ATERRAMENTO_COM_BASTAO -> {
            val d = closestPoleDiameter(stickDiameter_mm)
            val perPoint = nbr11854LeakageLimit_uA(d)
            val points = stickTestPoints.coerceAtLeast(1)
            // limite = quantidade de pontos ensaiados × limite da tabela
            val lim = perPoint * points
            Triple(lim, true, "ABNT NBR 11854 (Tabela de corrente de fuga — ${NBR11854_TEST_VOLTAGE_KV.toInt()} kV, bastão Ø $d mm: ${perPoint.toInt()} µA" +
                (if (points > 1) " × $points pontos = ${uaStr(lim)} µA)" else ")"))
        }
        EquipmentType.PONTEIRA_PROVA, EquipmentType.ATERRAMENTO_SEM_BASTAO -> Triple(criterion.maxLeakageCurrent, true, "ABNT NBR 6881 (item 4.3: sem perfuração; corrente de fuga: limite de ${criterion.maxLeakageCurrent} mA adotado pelo laboratório)")
        EquipmentType.DETECTOR_INDUCAO -> Triple(0.0, true, "Especificações do fabricante — detecção por indução, classe ${InductionDetector.DETECTOR_CLASS}")
        EquipmentType.MANTA_ISOLANTE -> Triple(criterion.maxLeakageCurrent, true, "ASTM D1048-14 (Itens 18.1 a 18.3 / Tabelas 1 e 2)")
        EquipmentType.TAPETE_ISOLANTE -> Triple(TAPETE_LEAKAGE_LIMIT_MA, true, "ASTM D178-22 (Itens 18.1 e 18.2 / Tabelas 1 e 2 - Limite de fuga adotado: 100 mA)")
        // escada: limite = quantidade de pontos ensaiados × limite da norma
        EquipmentType.ESCADA_ISOLADA -> {
            val points = stickTestPoints.coerceAtLeast(1)
            // limite por ponto = o do critério escolhido (EN 50528: 0,50 mA; NBR IEC 61478: 0,10 mA...)
            val perPoint = criterion.maxLeakageCurrent
            val lim = perPoint * points
            Triple(lim, true, criterion.normCode + if (points > 1) " ($points pontos ensaiados × ${fmt(perPoint, 2)} mA = ${fmt(lim, 2)} mA)" else "")
        }
        EquipmentType.FERRAMENTA_ISOLADA -> Triple(toolsCount.coerceAtLeast(1) * 1.0, true, "ABNT NBR 9699:2022 / IEC 60900 (Item 5.5 / 1,0 mA por ferramenta)")
        else -> Triple(criterion.maxLeakageCurrent, true, criterion.normCode)
    }

    /** Aterramento temporário não tem critério na web: mantém o critério próprio do app. */
    private fun legacyGroundingCriteria(dielectricClass: String): StandardNormCriteria {
        val c = cleanClass(dielectricClass)
        return StandardNormCriteria(
            normCode = "ABNT NBR IEC 61230 / ASTM F855",
            targetVoltage_kV = if (c == "0" || c == "00") 10.0 else if (c == "4") 40.0 else 20.0,
            durationSeconds = 60,
            leakageLimit_mA = 0.20,
            retestIntervalMonths = 12,
            description = "Ensaio de rigidez dielétrica nos bastões isolantes de manobra (ABNT NBR 16613), integridade dos grampos e cabos de aterramento (ABNT NBR IEC 61230 / ASTM F855). Critério próprio do aplicativo (sem critério correspondente na plataforma web).",
            normName = "Conjuntos de Aterramento Temporário (EPC)"
        )
    }

    fun getStandardCriteria(
        equipmentType: EquipmentType,
        dielectricClass: String,
        gloveLength_mm: Int = 360,
        toolsCount: Int = 1,
        stickDiameter_mm: Int = 38,
        criterionId: String? = null,
        stickTestPoints: Int = 1
    ): StandardNormCriteria {
        val criterion = findCriterion(equipmentType, dielectricClass, criterionId)
        if (criterion == null) {
            if (equipmentType == EquipmentType.ATERRAMENTO_TEMPORARIO) return legacyGroundingCriteria(dielectricClass)
            val dc = DielectricClassDefaults.testVoltage(cleanClass(dielectricClass))
            return StandardNormCriteria(
                normCode = "Critério técnico não configurado",
                targetVoltage_kV = dc,
                durationSeconds = 60,
                leakageLimit_mA = 0.0,
                retestIntervalMonths = 6,
                description = "Critério técnico não configurado. Consulte a norma/procedimento aplicável.",
                configured = false
            )
        }
        val (limit, gloveOk, clause) = appliedLimit(equipmentType, criterion, cleanClass(dielectricClass), gloveLength_mm, toolsCount, stickDiameter_mm, stickTestPoints)
        val stick = isStick(equipmentType)
        return StandardNormCriteria(
            normCode = criterion.normCode,
            targetVoltage_kV = criterion.testVoltage_kV,
            durationSeconds = criterion.testDurationSeconds,
            // bastões/varas: limite já em µA (NBR 11854), unidade usada nas telas do app
            leakageLimit_mA = limit,
            voltageType = criterion.voltageType,
            retestIntervalMonths = criterion.defaultRetestMonths,
            description = criterion.notes.ifBlank { criterion.normName },
            currentUnit = if (stick) "µA" else criterion.currentUnit,
            criterionId = criterion.id,
            normName = criterion.normName,
            gloveLengthApplicable = gloveOk,
            normReferenceClause = clause
        )
    }

    /** Tensão de prova padrão por classe (só para exibição quando não há critério). */
    private object DielectricClassDefaults {
        fun testVoltage(c: String) = when (c) {
            "00" -> 2.5; "0" -> 5.0; "1" -> 10.0; "2" -> 20.0; "3" -> 30.0; "4" -> 40.0; else -> 20.0
        }
    }

    data class EvaluationInput(
        val equipmentType: EquipmentType,
        val dielectricClass: String,
        val appliedVoltage_kV: Double,
        val durationSeconds: Int,
        /** Bastões/varas: valor em µA (como digitado nas telas do app). */
        val measuredLeakage_mA: Double,
        val withstandWithoutPuncture: Boolean,
        val visualPassed: Boolean,
        val gloveLength_mm: Int = 360,
        val toolsCount: Int = 1,
        val individualToolsRejectedCount: Int = 0,
        val rejectedToolsSummary: String = "",
        val customTargetVoltage_kV: Double? = null,
        val customLeakageLimit_mA: Double? = null,
        val customDurationSeconds: Int? = null,
        val customRetestMonths: Int? = null,
        val customNormReference: String? = null,
        val stickDiameter_mm: Int = 38,
        /** Bastões/varas e escada: quantidade de pontos ensaiados (limite = pontos × limite da tabela/norma). */
        val stickTestPoints: Int = 1,
        /** Detector por indução: distância (cm) de detecção em cada tensão da tabela */
        val inductionPoints: List<InductionPoint>? = null,
        /** Critério escolhido no seletor "Norma e Critério Técnico" (como na web). */
        val criterionId: String? = null,
        val visualFailedItems: List<String> = emptyList()
    )

    data class EvaluationOutput(
        val result: TestResult,
        val targetVoltage_kV: Double,
        val leakageLimit_mA: Double,
        val recommendedRetestMonths: Int,
        val rationale: String,
        val normReference: String,
        val durationSeconds: Int = 60,
        val isCustomized: Boolean = false,
        val criterionId: String = "",
        val canEvaluate: Boolean = true
    )

    fun evaluate(input: EvaluationInput, @Suppress("UNUSED_PARAMETER") customNorms: List<NormCriterionEntity> = emptyList()): EvaluationOutput {
        val std = getStandardCriteria(
            equipmentType = input.equipmentType,
            dielectricClass = input.dielectricClass,
            gloveLength_mm = input.gloveLength_mm,
            toolsCount = input.toolsCount,
            stickDiameter_mm = input.stickDiameter_mm,
            criterionId = input.criterionId,
            stickTestPoints = input.stickTestPoints
        )

        // Sem critério na lista: a web não inventa critério e não avalia
        if (!std.configured && input.customLeakageLimit_mA == null) {
            return EvaluationOutput(
                result = TestResult.PENDENTE,
                targetVoltage_kV = input.customTargetVoltage_kV ?: std.targetVoltage_kV,
                leakageLimit_mA = 0.0,
                recommendedRetestMonths = input.customRetestMonths ?: 6,
                rationale = "Critério técnico não configurado. Consulte a norma/procedimento aplicável.",
                normReference = input.customNormReference?.ifBlank { null } ?: std.normCode,
                durationSeconds = input.customDurationSeconds ?: std.durationSeconds,
                canEvaluate = false
            )
        }

        val type = input.equipmentType

        // Detector por indução: avaliação pela tabela distância × tensão (como na web)
        if (type == EquipmentType.DETECTOR_INDUCAO) {
            val ev = InductionDetector.evaluate(input.inductionPoints, if (input.visualPassed) emptyList() else input.visualFailedItems.ifEmpty { listOf("item não conforme") })
            return EvaluationOutput(
                result = ev.result,
                targetVoltage_kV = std.targetVoltage_kV,
                leakageLimit_mA = 0.0,
                recommendedRetestMonths = input.customRetestMonths ?: std.retestIntervalMonths,
                rationale = ev.rationale,
                normReference = input.customNormReference?.ifBlank { null } ?: std.normCode,
                durationSeconds = std.durationSeconds,
                criterionId = std.criterionId,
                // distâncias faltando: a web não conclui o ensaio
                canEvaluate = ev.result != TestResult.PENDENTE
            )
        }
        val stick = isStick(type)
        val dClass = cleanClass(input.dielectricClass)
        val targetVoltage = input.customTargetVoltage_kV ?: std.targetVoltage_kV
        val targetDuration = input.customDurationSeconds ?: std.durationSeconds
        val leakageLimit = input.customLeakageLimit_mA ?: std.leakageLimit_mA
        val retestMonths = input.customRetestMonths ?: std.retestIntervalMonths
        val normRef = input.customNormReference?.ifBlank { null } ?: std.normCode
        val isCustomized = input.customTargetVoltage_kV != null || input.customLeakageLimit_mA != null ||
            input.customDurationSeconds != null || input.customRetestMonths != null ||
            (input.customNormReference != null && input.customNormReference != std.normCode)
        val unit = if (stick) "µA" else std.currentUnit

        // Mesmas verificações da web (tolerância de 2% na tensão e 1 s no tempo)
        val isVisualOk = input.visualPassed
        val gloveOk = type != EquipmentType.LUVA_ISOLANTE || std.gloveLengthApplicable || input.customLeakageLimit_mA != null
        val isVoltageOk = input.appliedVoltage_kV >= targetVoltage * 0.98
        val isDurationOk = input.durationSeconds >= targetDuration - 1
        val isWithstandOk = input.withstandWithoutPuncture
        val isLeakageOk = gloveOk && input.measuredLeakage_mA <= leakageLimit
        val areToolsOk = input.individualToolsRejectedCount == 0

        val reasons = mutableListOf<String>()
        if (!isVisualOk) {
            reasons.add(
                if (input.visualFailedItems.isNotEmpty()) "Reprovado na inspeção visual: ${input.visualFailedItems.joinToString("; ")}."
                else "Reprovado na inspeção visual."
            )
        }
        if (!gloveOk) {
            reasons.add("Comprimento ${input.gloveLength_mm} mm não é aplicável para Luva Classe $dClass conforme ABNT NBR 16295 Tabela 4.")
        }
        if (!isWithstandOk) reasons.add("Ocorreu disrupção ou perfuração dielétrica (puncture/flashover) durante a aplicação de alta tensão.")
        if (!isVoltageOk) reasons.add("Tensão aplicada (${input.appliedVoltage_kV} kV) inferior à tensão normativa exigida ($targetVoltage kV).")
        if (!isDurationOk) reasons.add("Tempo de aplicação (${input.durationSeconds}s) inferior ao tempo normativo (${targetDuration}s).")
        if (gloveOk && !isLeakageOk) {
            reasons.add(
                when (type) {
                    EquipmentType.LUVA_ISOLANTE -> "Corrente de fuga medida (${fmt(input.measuredLeakage_mA)} mA) excedeu o limite máximo normativo de ${fmt(leakageLimit)} mA da ABNT NBR 16295 Tabela 4 para ensaio simultâneo de 2 luvas (Classe $dClass, ${input.gloveLength_mm} mm: ${fmt(leakageLimit / 2)} mA unitário × 2 luvas)."
                    EquipmentType.MANTA_ISOLANTE -> "Corrente de fuga medida (${input.measuredLeakage_mA} mA) excedeu o patamar aceitável de $leakageLimit mA conforme ASTM D1048-14."
                    EquipmentType.TAPETE_ISOLANTE -> "Corrente de fuga medida (${input.measuredLeakage_mA} mA) excedeu o limite máximo de $leakageLimit mA (a norma ASTM D178-22 não estipula valor específico de corrente de fuga, adotando-se o limite de 100 mA)."
                    EquipmentType.PONTEIRA_PROVA, EquipmentType.ATERRAMENTO_SEM_BASTAO -> "Corrente de fuga medida (${input.measuredLeakage_mA} mA) excedeu o limite de $leakageLimit mA adotado pelo laboratório (a ABNT NBR 6881 não estipula corrente de fuga; o critério da norma é a ausência de perfuração)."
                    EquipmentType.BASTAO_MANOBRA, EquipmentType.VARA_MANOBRA, EquipmentType.ATERRAMENTO_COM_BASTAO -> "Corrente de fuga medida (${input.measuredLeakage_mA} µA) excedeu o limite máximo de ${stickLimitText(leakageLimit, input)}."
                    EquipmentType.ESCADA_ISOLADA -> "Corrente de fuga medida (${fmt(input.measuredLeakage_mA, 2)} mA) excedeu o limite máximo de ${fmt(leakageLimit, 2)} mA (${kotlin.math.round(leakageLimit * 1000).toInt()} µA${ladderPointsText(leakageLimit, input)}) conforme $normRef."
                    EquipmentType.FERRAMENTA_ISOLADA -> "Corrente de fuga medida (${fmt(input.measuredLeakage_mA, 2)} mA) excedeu o limite normativo máximo de ${fmt(leakageLimit)} mA para o lote de ${input.toolsCount} ferramenta(s) ensaiada(s) (1,0 mA por ferramenta conforme ABNT NBR 9699 / IEC 60900)."
                    else -> "Corrente de fuga medida (${input.measuredLeakage_mA} $unit) excedeu o limite máximo permitido de ${if (stick) leakageLimit.toInt().toString() else leakageLimit.toString()} $unit conforme $normRef."
                }
            )
        }

        // Ferramentas manuais isoladas: pareceres por peça (como na web)
        val approvedTools = (input.toolsCount - input.individualToolsRejectedCount).coerceAtLeast(0)
        if (type == EquipmentType.FERRAMENTA_ISOLADA && input.toolsCount > 0) {
            if (!areToolsOk) {
                val reprovedDesc = input.rejectedToolsSummary.ifBlank { "${input.individualToolsRejectedCount} item(ns)" }
                val reprovedOpinion = "PARECER TÉCNICO 2 — FERRAMENTAS REPROVADAS (SEGREGAÇÃO E DESCARTE COMPULSÓRIO)\nNorma Aplicável: ABNT NBR 9699:2022 / IEC 60900 / NR-10 Item 10.4.1\nAs ${input.individualToolsRejectedCount} ferramenta(s) manuais isoladas listadas [$reprovedDesc] NÃO ATENDERAM aos critérios de aceitação durante a inspeção visual ou ensaio dielétrico a 10 kV CA, apresentando não-conformidades que comprometem a segurança do operador.\nCONCLUSÃO: CONSIDERADAS INAPTAS para intervenções elétricas. DETERMINA-SE A SEGREGAÇÃO IMEDIATA, APLICAÇÃO DE ETIQUETA VERMELHA DE CONDENAÇÃO E ENCAMINHAMENTO PARA DESCARTE / DESTRUIÇÃO MECÂNICA COMPULSÓRIA, SENDO PROIBIDA SUA UTILIZAÇÃO."
                if (approvedTools > 0) {
                    val approvedOpinion = "PARECER TÉCNICO 1 — FERRAMENTAS APROVADAS (LIBERAÇÃO OPERACIONAL)\nNorma Aplicável: ABNT NBR 9699:2022 / IEC 60900 / NR-10 Item 10.4\nAs $approvedTools ferramenta(s) manuais isoladas aprovadas foram ensaiadas individualmente sob tensão de prova de 10.000 V CA por 180 segundos contínuos (3 minutos) após condicionamento, apresentando integridade plena da isolação, ausência de perfuração dielétrica e corrente de fuga controlada inferior ao limite normativo de ${fmt(approvedTools * 1.0)} mA (1,0 mA por ferramenta ensaiada).\nCONCLUSÃO: CONSIDERADAS APTAS para utilização profissional em instalações elétricas energizadas até 1.000 V CA / 1.500 V CC. EMISSÃO AUTORIZADA DO CERTIFICADO DE CONFORMIDADE DIELÉTRICA."
                    reasons.add("$approvedOpinion\n\n$reprovedOpinion")
                } else {
                    reasons.add(reprovedOpinion)
                }
            }
        }

        val approved = reasons.isEmpty() && areToolsOk
        val result = if (approved) TestResult.APROVADO else TestResult.REPROVADO

        val rationale = if (!approved) {
            reasons.joinToString("\n\n")
        } else when (type) {
            EquipmentType.LUVA_ISOLANTE ->
                "Equipamento plenamente CONFORME com a ABNT NBR 16295:2023 Tabela 4 (Ensaio de Prova item 8.4.2.1 / 8.4.3.1). " +
                    "Atendeu à inspeção visual com inflamento de ar (item 8.2), suportabilidade dielétrica a $targetVoltage kV ${std.voltageType} por ${input.durationSeconds} segundos sem perfuração, " +
                    "com corrente de fuga de ${fmt(input.measuredLeakage_mA)} mA (abaixo do limite máximo normativo de ${fmt(leakageLimit)} mA para o ensaio simultâneo de 2 luvas na cuba, referente ao limite unitário de ${fmt(leakageLimit / 2)} mA × 2 da Tabela 4 para Classe $dClass, comprimento ${input.gloveLength_mm} mm)."
            EquipmentType.MANTA_ISOLANTE ->
                "Manta Isolante plenamente CONFORME com a ASTM D1048-14 (Tabelas 1 e 2 / Item 18). " +
                    "Aprovada na inspeção visual de integridade de superfície e suportabilidade dielétrica ao Ensaio de Prova com $targetVoltage kV ${std.voltageType} contínuos por ${input.durationSeconds} segundos entre eletrodos planos, " +
                    "sem ocorrência de perfuração dielétrica ou descarga disruptiva (withstand without puncture/flashover), com corrente de fuga de ${fmt(input.measuredLeakage_mA)} mA (limite: $leakageLimit mA)."
            EquipmentType.TAPETE_ISOLANTE ->
                "Tapete Isolante plenamente CONFORME com a ASTM D178-22 (Tabelas 1 e 2 / Item 18). " +
                    "Aprovado na inspeção visual de integridade superficial e ensaio dielétrico de prova a $targetVoltage kV ${std.voltageType} por ${input.durationSeconds} segundos por segmento sem disrupção nem perfuração, " +
                    "com corrente de fuga estável em ${fmt(input.measuredLeakage_mA)} mA (abaixo do limite de $leakageLimit mA; a norma ASTM D178-22 não estipula valor específico de corrente de fuga, adotando-se o limite de 100 mA)."
            EquipmentType.ESCADA_ISOLADA ->
                "Escada Isolada em Fibra de Vidro (PRFV) plenamente CONFORME com a norma ${std.normCode} (${std.normName}). " +
                    "Aprovada na inspeção visual e mecânica de integridade dos montantes, degraus, catracas e sapatas antiderrapantes. " +
                    "Atendeu ao ensaio dielétrico de $targetVoltage kV ${std.voltageType} por ${input.durationSeconds} segundos a seco com suportabilidade plena sem disrupção ou perfuração, " +
                    "registrando corrente de fuga de ${fmt(input.measuredLeakage_mA, 2)} mA (${(input.measuredLeakage_mA * 1000).toInt()} µA), estritamente abaixo do limite de ${fmt(leakageLimit, 2)} mA (${kotlin.math.round(leakageLimit * 1000).toInt()} µA${ladderPointsText(leakageLimit, input)})."
            EquipmentType.CAPACETE_CLASSE_B ->
                "Capacete de Segurança Classe B (Classe Dielétrica 2 — Até 20.000 V) plenamente CONFORME com a ABNT NBR 8221 / ANSI Z89.1. " +
                    "Aprovado na inspeção visual do casco, carneira, jugular e suspensão, suportabilidade dielétrica a $targetVoltage kV ${std.voltageType} durante ${input.durationSeconds} segundos (3 minutos) após imersão em água por 24h sem perfuração ou descarga disruptiva, " +
                    "com corrente de fuga de ${fmt(input.measuredLeakage_mA)} mA (estritamente abaixo do limite máximo de ${fmt(leakageLimit)} mA para Classe 2 / Classe B)."
            EquipmentType.PONTEIRA_PROVA, EquipmentType.ATERRAMENTO_SEM_BASTAO ->
                (if (type == EquipmentType.ATERRAMENTO_SEM_BASTAO) "Conjunto de aterramento temporário sem bastão CONFORME pelo critério de ponteiras de prova da ABNT NBR 6881. "
                else "Ponteiras de prova / cabos de ensaio CONFORMES com a ABNT NBR 6881. ") +
                "Ensaio de tensão aplicada a $targetVoltage kV ${std.voltageType} por ${input.durationSeconds} segundos, com a tensão elevada a partir de no máximo 20 % da tensão nominal (${fmt(PROBE_RAMP_START_MAX_KV).replace(".", ",")} kV) em 10 s a 60 s e reduzida até no máximo 20 % antes do desligamento, sem perfuração total ou parcial da isolação (item 4.3 da norma). Corrente de fuga registrada: ${input.measuredLeakage_mA} mA (limite de $leakageLimit mA adotado pelo laboratório; a NBR 6881 não estipula valor de corrente de fuga)."
            EquipmentType.BASTAO_MANOBRA, EquipmentType.VARA_MANOBRA, EquipmentType.ATERRAMENTO_COM_BASTAO -> {
                val kind = when (type) {
                    EquipmentType.VARA_MANOBRA -> "Vara de manobra telescópica / seccionável"
                    EquipmentType.ATERRAMENTO_COM_BASTAO -> "Conjunto de aterramento temporário com bastão (critério de vara de manobra)"
                    else -> "Bastão de manobra / bastão de resgate"
                }
                "$kind plenamente CONFORME com a ABNT NBR 11854. Aprovado na inspeção visual e no ensaio dielétrico a $targetVoltage kV ${std.voltageType} por ${input.durationSeconds} segundos, sem disrupção nem perfuração, " +
                    "com corrente de fuga de ${input.measuredLeakage_mA} µA (abaixo do limite de ${stickLimitText(leakageLimit, input)})."
            }
            EquipmentType.FERRAMENTA_ISOLADA ->
                "Lote de ${input.toolsCount} ferramenta(s) manuais isoladas ensaiadas individualmente sob tensão de prova de 10.000 V CA por 180 segundos contínuos (3 minutos) após condicionamento, conforme ABNT NBR 9699:2022 / IEC 60900 / NR-10 Item 10.4.\n" +
                    "Todas as peças apresentaram plena integridade da isolação, ausência de perfuração dielétrica e corrente de fuga total de ${fmt(input.measuredLeakage_mA)} mA (estritamente abaixo do limite normativo de ${fmt(leakageLimit)} mA, à razão de 1,0 mA por ferramenta).\n" +
                    "CONCLUSÃO: Lote considerado APTO para intervenções e serviços profissionais em instalações elétricas energizadas até 1.000 V CA / 1.500 V CC. EMISSÃO AUTORIZADA DO CERTIFICADO DE CONFORMIDADE DIELÉTRICA."
            else ->
                "Equipamento plenamente CONFORME. Atendeu à inspeção visual, suportabilidade dielétrica a $targetVoltage kV ${std.voltageType} por $targetDuration segundos, com corrente de fuga de ${input.measuredLeakage_mA} $unit (abaixo do limite de ${if (stick) leakageLimit.toInt().toString() else leakageLimit.toString()} $unit da norma $normRef)."
        }

        return EvaluationOutput(
            result = result,
            targetVoltage_kV = targetVoltage,
            leakageLimit_mA = leakageLimit,
            recommendedRetestMonths = retestMonths,
            rationale = rationale,
            normReference = normRef,
            durationSeconds = targetDuration,
            isCustomized = isCustomized,
            criterionId = std.criterionId
        )
    }
}
