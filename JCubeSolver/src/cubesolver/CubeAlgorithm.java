package cubesolver;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class CubeAlgorithm {

    private final Charset CSASC = Charset.forName("ASCII");
    private final byte CHAROFFSET = 'A';
    // RLFBUD is the face order used for input, so that a correctly oriented
    // piece in the input has its 'highest value' facelet first. The rest of the
    // program uses moves in FBRLUD order.
    // input:  UF UR UB UL  DF DR DB DL  FR FL BR BL  UFR URB UBL ULF   DRF DFL DLB DBR
    //         A  B  C  D   E  F  G  H   I  J  K  L   M   N   O   P     Q   R   S   T   
    //         A  E  C  G   B  F  D  H   I  J  K  L   M   S   N   T     R   O   Q   P
    // intrnl: UF DF UB DB  UR DR UL DL  FR FL BR BL  UFR UBL DFL DBR   DLB DRF URB ULF
    private final byte[] order = "AECGBFDHIJKLMSNTROQP".getBytes(CSASC);
    // intrnl: UF DF UB DB  UR DR UL DL  FR FL BR BL  UFR UBL DFL DBR   DLB DRF URB ULF
    // bithash:20,36,24,40, 17,33,18,34, 5, 6, 9, 10, 21, 26, 38, 41,   42, 37, 25, 22
    private final byte[] bithash = "TdXhQaRbEFIJUZfijeYV".getBytes(CSASC);
    // Each move consists of two 4-cycles. This string contains these in FBRLUD order.
    // intrnl: UF DF UB DB  UR DR UL DL  FR FL BR BL  UFR UBL DFL DBR   DLB DRF URB ULF 
    //         A  B  C  D   E  F  G  H   I  J  K  L   M   N   O   P     Q   R   S   T  
    private final byte[] perm = "AIBJTMROCLDKSNQPEKFIMSPRGJHLNTOQAGCEMTNSBFDHORPQ".getBytes(CSASC);
    private final int[] val = {2,2,2,2,2,2,2,2,2,2,2,2,3,3,3,3,3,3,3,3};
    // input:                   UF     UR     UB     UL    DF     DR     DB     DL    FR     FL    BR     BL    UFR       URB       UBL      ULF      DRF       DFL      DLB      DBR
    private final int[] imap = {53,15, 51,24, 49,44, 47,6, 29,11, 31,20, 33,40, 35,2, 17,22, 13,8, 42,26, 38,4, 52,16,23, 50,25,43, 48,37,5, 46,7,14, 30,21,10, 28,12,1, 34,3,39, 32,41,19};
    private final int[] order_r = {0, 4, 1, 5};
    private final int[] order_s = {0, 2, 1, 3};
    private final int[] tablesize = {1,4096,  6561,4096,  256,1536,  13824,576};

    private byte[][] tables;
    private byte[] pos;
    private byte[] ori;
    private int[] move;
    private int[] moveamount;
    private int phase = 0;
    private int _mode_o = 1;

    public CubeAlgorithm() throws Exception {
        // Load table data from resource file
        byte[] buf = new byte[30946];
        InputStream istrm = CubeAlgorithm.class.getResourceAsStream("tabledata");
        istrm.read(buf);
        
        tables = new byte[8][];
        int k = 0;
        for (int i = 0; i < 8; i++) {
            tables[i] = new byte[tablesize[i]];
            for (int j = 0; j < tablesize[i]; j++) {
                tables[i][j] = (byte)(buf[k] - CHAROFFSET);
                k++;
            }
        }

        pos = new byte[20];
        ori = new byte[20];
        move = new int[20];
        moveamount = new int[20];
    }

    public String solve() throws UnsupportedEncodingException {
        StringBuffer sb = new StringBuffer();
        // internal order
        String ifaces = "FBRLUD";
        // F=0,B=1,R=2,L=3,U=4,D=5, index:move-face, value:current-face
        int[] faces_mc = {0, 1, 2, 3, 4, 5};
        // index:current-face, value:move-face
        int[] faces_cm = {0, 1, 2, 3, 4, 5};
        for (phase = 0; phase < 8; phase += 2) {
            int j = 0;
            // try each depth till solved
            for(; searchphase(j, 0, 9) == 0; j++);
            // output result of this phase
            for(int i = 0; i < j; i++) {
                if (_mode_o == 0) {
                    sb.append(ifaces.substring(move[i], move[i] + 1));
                    sb.append(moveamount[i]);
                } else {
                    sb.append(tosrt(move[i], moveamount[i], faces_mc, faces_cm));
                }
            }
        }
        return compactstep(sb.toString());
    }

    // WYBBYBGGWRYYRRRBYGYYOBRRYGBGBOOGGWROOWBWOORWRBWWOYOGGW
    public CubeAlgorithm setInput(byte[] input) throws UnsupportedEncodingException {
        // argument check
        if (input.length != 54) {
            throw new IllegalArgumentException("arguments error!");
        }
        String sinput = new String(input, "ASCII");
        String faces = sinput.substring(18, 19) + sinput.substring(0, 1) + sinput.substring(9, 10) +
                       sinput.substring(36, 37) + sinput.substring(45, 46) + sinput.substring(27, 28);
        int l = 0;
        for (int i = 0; i < 20; i++) {
            int f = 0, pc = 0, k = 0, mor = 0;
            for (; f < val[i]; f++) {
                int j = faces.indexOf(sinput.substring(imap[l], imap[l] + 1));
                l++;
                // keep track of principal facelet for orientation
                if (j > k) {
                    k = j;
                    mor = f;
                }
                //construct bit hash code
                pc += 1<<j;
            }
            // find which cubelet it belongs, i.e. the label for this piece
            for (f = 0; f < 20; f++) {
                if (pc == bithash[f] - 64)
                    break;
            }
            // store piece
            pos[order[i] - CHAROFFSET] = (byte)f;
            ori[order[i] - CHAROFFSET] = (byte)mor;
        }
        return this;
    }

    // FL RD BR UB UR DF BL UL FU BD DL RF ULF UBL FRU URB DRF BRD FLD DLB
    public CubeAlgorithm setInput(String argstr) {
        // argument check
        if (argstr.length() != 67) {
            throw new IllegalArgumentException("arguments error!");
        }
        String[] args = argstr.split(" ");
        return setInput(args);
    }

    // FL RD BR UB UR DF BL UL FU BD DL RF ULF UBL FRU URB DRF BRD FLD DLB
    public CubeAlgorithm setInput(String[] args) {
        // argument check
        if (args.length != 20) {
            throw new IllegalArgumentException("arguments error!");
        }
        String faces = "RLFBUD";
        for (int i = 0; i < 20; i++) {
            int f = 0, pc = 0, k = 0, mor = 0;
            if (args[i].length() != val[i]) {
                throw new IllegalArgumentException("arguments error!");
            }
            for (; f < val[i]; f++) {
                int j = faces.indexOf(args[i].substring(f, f + 1));
                // keep track of principal facelet for orientation
                if (j > k) {
                    k = j;
                    mor = f;
                }
                //construct bit hash code
                pc += 1<<j;
            }
            // find which cubelet it belongs, i.e. the label for this piece
            for (f = 0; f < 20; f++) {
                if (pc == bithash[f] - 64)
                    break;
            }
            // store piece
            pos[order[i] - CHAROFFSET] = (byte)f;
            ori[order[i] - CHAROFFSET] = (byte)mor;
        }
        return this;
    }

    public CubeAlgorithm setModeO(int mode) {
        _mode_o = mode;
        return this;
    }

    private String compactstep(String movestep) throws UnsupportedEncodingException {
        byte[] ms = movestep.getBytes(CSASC);
        int len = ms.length;
        if (len < 2) {
            return movestep;
        }
        int j = 0;
        for (int i = 2; i < len; i += 2) {
            if (ms[i] == ms[j]) {
                ms[j + 1] = (byte)((ms[j + 1] - '0' + ms[i + 1] - '0')%4 + '0');
            } else {
                j += 2;
            }
        }
        j += 2;
        if (j == len) {
            return movestep;
        }
        byte[] bret = new byte[j];
        for (int k = 0; k < j; k++) {
            bret[k] = ms[k];
        }
        return new String(bret, "ASCII");
    }

    private void rollorspin(int step, int[] ord, int[] faces_mc, int[] faces_cm) {
        int i = 0;
        for (; i < step; i++) {
            int tmp = faces_cm[ord[3]];
            faces_cm[ord[3]] = faces_cm[ord[2]];
            faces_cm[ord[2]] = faces_cm[ord[1]];
            faces_cm[ord[1]] = faces_cm[ord[0]];
            faces_cm[ord[0]] = tmp;
        }
        for (i = 0; i < 6; i++){
            faces_mc[faces_cm[i]] = i;
        }
    }

    private String tosrt(int mi, int step, int[] faces_mc, int[] faces_cm) {
        String retstr = "";
        switch (faces_mc[mi]) {
        case 0:
            rollorspin(2, order_s, faces_mc, faces_cm);
            rollorspin(1, order_r, faces_mc, faces_cm);
            retstr = "S2R1";
            break;
        case 1:
            rollorspin(1, order_r, faces_mc, faces_cm);
            retstr = "R1";
            break;
        case 2:
            rollorspin(1, order_s, faces_mc, faces_cm);
            rollorspin(1, order_r, faces_mc, faces_cm);
            retstr = "S1R1";
            break;
        case 3:
            rollorspin(3, order_s, faces_mc, faces_cm);
            rollorspin(1, order_r, faces_mc, faces_cm);
            retstr = "S3R1";
            break;
        case 4:
            rollorspin(2, order_r, faces_mc, faces_cm);
            retstr = "R2";
            break;
        default:
            break;
        }
        if (step == 1) {
            retstr += "T1";
        } else if (step == 2) {
            retstr += "T2";
        } else {
            retstr += "T3";
        }
        return retstr;
    }

    // Cycles 4 pieces in array p, the piece indices given by a[0..3].
    private void cycle(byte[] p, byte[] a, int aoffset) {
        byte temp = p[a[3 + aoffset] - CHAROFFSET];
        p[a[3 + aoffset] - CHAROFFSET] = p[a[2 + aoffset] - CHAROFFSET];
        p[a[2 + aoffset] - CHAROFFSET] = p[a[1 + aoffset] - CHAROFFSET];
        p[a[1 + aoffset] - CHAROFFSET] = p[a[0 + aoffset] - CHAROFFSET];
        p[a[0 + aoffset] - CHAROFFSET] = temp;
    }

    // twists i-th piece a+1 times.
    private void twist(int i, int a) {
        i -= CHAROFFSET;
        ori[i] = (byte)((ori[i] + a + 1)%val[i]);
    }

    // convert permutation of 4 chars to a number in range 0..23
    private int permtonum(byte[] p, int offset) {
        int n = 0;
        int a = 0;
        for (; a < 4; a++) {
            int b = a;
            n *= 4 - a;
            for (; ++b < 4;)
                if (p[b + offset] < p[a + offset]) n++;
        }
        return n;
    }

    // get index of cube position from table t
    private int getposition(int t) {
        int i = -1, n = 0;
        switch (t) {
        // case 0 does nothing so returns 0
        case 1: //edgeflip
            // 12 bits, set bit if edge is flipped
            for (; ++i < 12;) n += ori[i]<<i;
            break;
        case 2: //cornertwist
            // get base 3 number of 8 digits - each digit is corner twist
            for (i = 20; --i > 11;) n = n*3 + ori[i];
            break;
        case 3: //middle edge choice
            // 12 bits, set bit if edge belongs in Um middle slice
            for (; ++i < 12;) n += ((pos[i]&8) != 0) ? (1<<i) : 0;
            break;
        case 4: //ud slice choice
            // 8 bits, set bit if UD edge belongs in Fm middle slice
            for (; ++i < 8;) n += ((pos[i]&4) != 0) ? (1<<i) : 0;
            break;
        case 5: //tetrad choice, twist and parity
        {
            int[] corn = {0, 0, 0, 0, 0, 0, 0, 0};
            int[] corn2 = {0, 0, 0, 0};
            int j, k, l;
            // 8 bits, set bit if corner belongs in second tetrad.
            // also separate pieces for twist/parity determination
            k = j = 0;
            for (; ++i < 8;)
                if (((l = pos[i+12] - 12)&4) != 0) {
                    corn[l] = k++;
                    n += 1<<i;
                } else corn[j++] = l;
            // Find permutation of second tetrad after solving first
            for (i = 0; i < 4; i++) corn2[i] = corn[4+corn[i]];
            // Solve one piece of second tetrad
            for (; --i > 0;) corn2[i] ^= corn2[0];

            // encode parity/tetrad twist
            n = n * 6 + corn2[1] * 2 - 2;
            if (corn2[3] < corn2[2]) n++;
            break;
        }
        case 6://two edge and one corner orbit, permutation
            n = permtonum(pos, 0) * 576 + permtonum(pos, 4) * 24 + permtonum(pos, 12);
            break;
        case 7://one edge and one corner orbit, permutation
            n = permtonum(pos, 8) * 24 + permtonum(pos, 16);
            break;
        }
        return n;
    }

    // do a clockwise quarter turn cube move
    private void domove(int m) {
        int i = 8;
        //cycle the edges
        cycle(pos, perm, 8 * m);
        cycle(ori, perm, 8 * m);
        //cycle the corners
        cycle(pos, perm, 8 * m + 4);
        cycle(ori, perm, 8 * m + 4);
        //twist corners if RLFB
        if (m < 4)
            for (; --i > 3;) twist(perm[8 * m + i], i&1);
        //flip edges if FB
        if (m < 2)
            for (i = 4; i-- > 0;) twist(perm[8 * m + i], 0);
    }

    // Pruned tree search. recursive.
    private int searchphase(int movesleft, int movesdone, int lastmove) {
        int i = 6;
        // prune - position must still be solvable in the remaining moves available
        if (tables[phase  ][getposition(phase  )] - 1 > movesleft ||
            tables[phase+1][getposition(phase+1)] - 1 > movesleft ) return 0;

        // If no moves left to do, we have solved this phase
        if (movesleft == 0) return 1;

        // not solved. try each face move
        for (; i-- > 0;) {
            // do not repeat same face, nor do opposite after DLB.
            // if ((i - lastmove) != 0 && ((i - lastmove + 1) != 0 || (i|1) != 0))
            if ((i - lastmove) != 0) {
                int j = 0;
                move[movesdone] = i;
                // try 1,2,3 quarter turns of that face
                for (; ++j < 4;) {
                    // do move and remember it
                    domove(i);
                    moveamount[movesdone] = j;
                    //Check if phase only allows half moves of this face
                    if ((j == 2 || i >= phase ) &&
                        //search on
                        searchphase(movesleft - 1, movesdone + 1, i) != 0) return 1;
                }
                // put face back to original position.
                domove(i);
            }
        }
        // no solution found
        return 0;
    }

}
